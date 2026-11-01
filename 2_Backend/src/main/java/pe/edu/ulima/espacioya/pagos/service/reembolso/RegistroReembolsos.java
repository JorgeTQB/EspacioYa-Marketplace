package pe.edu.ulima.espacioya.pagos.service.reembolso;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ReembolsoRealizado;

/**
 * Las transacciones cortas del comando (HU-16 DT-5), cada una {@code REQUIRES_NEW}: tras el commit de la cancelación
 * la transacción original sigue ligada al hilo y una {@code REQUIRED} no escribiría. Orden de bloqueos: reembolso →
 * pago. Es el único lugar que cambia {@code reembolso.estado} y {@code pago.estado} tras pagar (D1).
 */
@Component
public class RegistroReembolsos {

    public static final String ACCION_REEMBOLSO_REALIZADO = "REEMBOLSO_REALIZADO";
    public static final String ACCION_REEMBOLSO_FALLIDO = "REEMBOLSO_FALLIDO";

    private static final String ENTIDAD_REEMBOLSO = "reembolso";

    private final ReembolsoRepository reembolsos;
    private final PagoRepository pagos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;

    public RegistroReembolsos(ReembolsoRepository reembolsos, PagoRepository pagos, AuditoriaService auditoria,
                              PlatformTransactionManager transacciones) {
        this.reembolsos = reembolsos;
        this.pagos = pagos;
        this.auditoria = auditoria;
        this.transacciones = new TransactionTemplate(transacciones);
        this.transacciones.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reclama el intento: vacío si no existe, ya está {@code REEMBOLSADO} o tiene otro intento en proceso (R18, R21,
     * R22). El primero (sin intentos) siempre se admite: es el automático (R5).
     */
    public Optional<IntentoReembolso> iniciar(Long reembolsoId, Instant ahora) {
        return transacciones.execute(estado -> {
            var encontrado = reembolsos.findParaActualizar(reembolsoId);
            if (encontrado.isEmpty() || !encontrado.get().admiteIntento(ahora)) {
                return Optional.<IntentoReembolso>empty();
            }
            var reembolso = encontrado.get();
            reembolso.iniciarIntento(ahora);
            var pago = pagos.findById(reembolso.getPagoId()).orElseThrow();
            return Optional.of(new IntentoReembolso(reembolso.getId(), pago.getId(), pago.getReservaId(),
                    pago.getIdTransaccionPasarela(), reembolso.getMonto(), reembolso.getIntentos(),
                    reembolso.claveIdempotencia()));
        });
    }

    /** Reembolso y pago {@code REEMBOLSADO} y {@code REEMBOLSO_REALIZADO}, en una transacción (R10, R11, R36, R38). */
    public void registrarExito(IntentoReembolso intento, ReembolsoRealizado realizado, SolicitudReembolso comando) {
        transacciones.executeWithoutResult(estado -> {
            var reembolso = reembolsos.findParaActualizar(intento.reembolsoId()).orElseThrow();
            var pago = pagos.findById(intento.pagoId()).orElseThrow();
            reembolso.marcarReembolsado(realizado.idReembolso());
            pago.marcarReembolsado();
            auditar(ACCION_REEMBOLSO_REALIZADO, intento, comando,
                    detalle(intento, comando) + ";idReembolso=" + realizado.idReembolso());
        });
    }

    /** Reembolso {@code FALLIDO}, pago {@code REEMBOLSO_FALLIDO} y {@code REEMBOLSO_FALLIDO} (R17, R37, R38). */
    public void registrarFallo(IntentoReembolso intento, SolicitudReembolso comando) {
        transacciones.executeWithoutResult(estado -> {
            var reembolso = reembolsos.findParaActualizar(intento.reembolsoId()).orElseThrow();
            var pago = pagos.findById(intento.pagoId()).orElseThrow();
            reembolso.marcarFallido();
            pago.marcarReembolsoFallido();
            auditar(ACCION_REEMBOLSO_FALLIDO, intento, comando, detalle(intento, comando));
        });
    }

    private void auditar(String accion, IntentoReembolso intento, SolicitudReembolso comando, String detalle) {
        auditoria.registrar(new EventoAuditoria(comando.solicitadoPor(), accion, ENTIDAD_REEMBOLSO,
                intento.reembolsoId(), comando.ip(), detalle));
    }

    // Solo datos propios: nada de la respuesta del proveedor llega al registro (R37).
    private static String detalle(IntentoReembolso intento, SolicitudReembolso comando) {
        return "pagoId=" + intento.pagoId() + ";reservaId=" + intento.reservaId() + ";intento=" + intento.numero()
                + ";disparo=" + comando.disparo() + ";monto=" + intento.monto().toPlainString();
    }
}
