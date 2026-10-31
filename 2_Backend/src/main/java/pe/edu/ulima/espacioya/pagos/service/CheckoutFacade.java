package pe.edu.ulima.espacioya.pagos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.dto.CheckoutDTO;
import pe.edu.ulima.espacioya.pagos.dto.EstadoPagoDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;

/**
 * Facade: la única entrada de {@code PagoController} para iniciar un pago y leer su estado. Coordina a
 * {@link EvaluacionCobro} y {@link SesionesCheckout}, sin reglas propias, y no confirma nada: la reserva solo cambia
 * con una notificación firmada (RNF-CUM-02).
 */
@Service
public class CheckoutFacade {

    public static final String ACCION_CHECKOUT_INICIADO = "CHECKOUT_INICIADO";

    private static final String ENTIDAD_RESERVA = "reserva";

    private final EvaluacionCobro evaluacion;
    private final SesionesCheckout sesiones;
    private final PagoRepository pagos;
    private final AuditoriaService auditoria;

    public CheckoutFacade(EvaluacionCobro evaluacion, SesionesCheckout sesiones, PagoRepository pagos,
                          AuditoriaService auditoria) {
        this.evaluacion = evaluacion;
        this.sesiones = sesiones;
        this.pagos = pagos;
        this.auditoria = auditoria;
    }

    // Sin @Transactional: la pasarela se llama por HTTP y ninguna llamada externa debe retener filas (DT-3).
    public CheckoutDTO iniciar(UsuarioAutenticado usuario, String reservaId, String ip) {
        var reserva = evaluacion.exigirDisponible(usuario, reservaId);
        var url = sesiones.urlPara(reserva);
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_CHECKOUT_INICIADO, ENTIDAD_RESERVA,
                reserva.id(), ip, "monto=" + reserva.montoTotal().toPlainString()));
        return new CheckoutDTO(url);
    }

    @Transactional(readOnly = true)
    public EstadoPagoDTO estado(UsuarioAutenticado usuario, String reservaId) {
        var resumen = evaluacion.resumen(usuario, reservaId);
        var ultimoResultado = pagos.findFirstByReservaIdOrderByCreadoEnDescIdDesc(resumen.reservaId())
                .map(pago -> pago.getEstado().name())
                .orElse(null);
        return new EstadoPagoDTO(ultimoResultado, resumen.intentosAgotados(), resumen.pagoDisponible());
    }
}
