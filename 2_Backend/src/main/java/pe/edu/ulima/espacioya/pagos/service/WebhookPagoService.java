package pe.edu.ulima.espacioya.pagos.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.ReglasPago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.NotificacionRecibida;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PagoConsultado;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ResultadoPasarela;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaBloqueada;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;

/**
 * Procesa las notificaciones de la pasarela: es el único camino que cambia una reserva a {@code CONFIRMADA}
 * (RNF-CUM-02). Firma → consulta, sin transacción para que ninguna llamada externa retenga filas → registro bajo los
 * bloqueos de espacio y reserva, que serializan dos avisos iguales y al webhook con la expiración (HU-13 DT-5).
 */
@Service
public class WebhookPagoService {

    public static final String ACCION_PAGO_SIN_RESERVA = "PAGO_SIN_RESERVA";

    private static final String ENTIDAD_PAGO = "pago";
    private static final String RESTRICCION_TRANSACCION = "uq_pago_id_transaccion";
    private static final Map<EstadoPago, String> ACCIONES = Map.of(
            EstadoPago.PAGADO, "PAGO_APROBADO",
            EstadoPago.RECHAZADO, "PAGO_RECHAZADO",
            EstadoPago.PAGADO_TARDIO, "PAGO_TARDIO",
            EstadoPago.MONTO_DISTINTO, "PAGO_MONTO_DISTINTO");

    private final PasarelaPago pasarela;
    private final ReservasPorCobrar reservas;
    private final PagoRepository pagos;
    private final SesionesCheckout sesiones;
    private final AvisosPago avisos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;

    public WebhookPagoService(PasarelaPago pasarela, ReservasPorCobrar reservas, PagoRepository pagos,
                              SesionesCheckout sesiones, AvisosPago avisos, AuditoriaService auditoria,
                              TransactionTemplate transacciones) {
        this.pasarela = pasarela;
        this.reservas = reservas;
        this.pagos = pagos;
        this.sesiones = sesiones;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
    }

    // Sin @Transactional: con él, la violación del UNIQUE llegaría al confirmar y no se podría reconocer aquí.
    public void recibir(NotificacionRecibida notificacion, String ip) {
        var idNotificado = pasarela.idNotificado(notificacion);
        if (idNotificado.isEmpty()) {
            return;
        }
        var consultado = pasarela.consultarPago(idNotificado.get());
        if (consultado.resultado() == ResultadoPasarela.SIN_RESULTADO) {
            return;
        }
        try {
            transacciones.executeWithoutResult(estado -> registrar(consultado, ip));
        } catch (DataIntegrityViolationException conflicto) {
            if (!esPagoYaRegistrado(conflicto)) {
                throw conflicto;
            }
        }
    }

    private void registrar(PagoConsultado consultado, String ip) {
        var reserva = bloquear(consultado.referencia());
        if (reserva.isEmpty()) {
            auditoria.registrar(new EventoAuditoria(null, ACCION_PAGO_SIN_RESERVA, ENTIDAD_PAGO, null, ip,
                    "origen=WEBHOOK;transaccion=" + consultado.idTransaccion()));
            return;
        }
        if (pagos.existsByIdTransaccionPasarela(consultado.idTransaccion())) {
            return;
        }
        var bloqueada = reserva.get();
        var estado = clasificar(consultado, bloqueada);
        var monto = montoARegistrar(consultado, bloqueada, estado);
        var pago = pagos.saveAndFlush(Pago.de(bloqueada.id(), consultado.idTransaccion(), monto, estado,
                consultado.ultimos4(), bloqueada.evaluadaEn()));
        auditoria.registrar(new EventoAuditoria(null, ACCIONES.get(estado), ENTIDAD_PAGO, pago.getId(), ip,
                "origen=WEBHOOK;reservaId=" + bloqueada.id() + ";transaccion=" + consultado.idTransaccion()
                        + ";monto=" + monto.toPlainString()));
        if (estado == EstadoPago.PAGADO) {
            reservas.confirmar(bloqueada.id(), bloqueada.evaluadaEn());
            avisos.programar(bloqueada, consultado);
        } else if (estado == EstadoPago.RECHAZADO && intentosAgotados(bloqueada)) {
            DespuesDelCommit.ejecutar(() -> sesiones.cerrar(bloqueada.id()));
        }
    }

    private Optional<ReservaBloqueada> bloquear(String referencia) {
        var reservaId = ReglasPago.idDeReserva(referencia);
        return reservaId == null ? Optional.empty() : reservas.bloquear(reservaId);
    }

    private static EstadoPago clasificar(PagoConsultado consultado, ReservaBloqueada reserva) {
        if (consultado.resultado() == ResultadoPasarela.RECHAZADO) {
            return EstadoPago.RECHAZADO;
        }
        return ReglasPago.estadoDeAprobado(consultado.monto(), consultado.moneda(), reserva.montoTotal(),
                reserva.vigente());
    }

    private static BigDecimal montoARegistrar(PagoConsultado consultado, ReservaBloqueada reserva, EstadoPago estado) {
        var coincide = estado == EstadoPago.PAGADO || estado == EstadoPago.PAGADO_TARDIO;
        return coincide ? reserva.montoTotal() : consultado.monto();
    }

    private boolean intentosAgotados(ReservaBloqueada reserva) {
        return ReglasPago.intentosAgotados(pagos.countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(reserva.id(),
                EstadoPago.RECHAZADO, ReglasPago.inicioVentanaRechazos(reserva.evaluadaEn())));
    }

    private static boolean esPagoYaRegistrado(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(RESTRICCION_TRANSACCION);
    }
}
