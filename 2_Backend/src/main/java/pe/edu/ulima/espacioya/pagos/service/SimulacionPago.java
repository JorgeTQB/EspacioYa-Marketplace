package pe.edu.ulima.espacioya.pagos.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.dto.SimulacionDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaSimulada;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ResultadoPasarela;

/**
 * Hace de "página de la pasarela" cuando la pasarela es la simulada (HU-13 D9): comprueba el permiso y entrega el
 * resultado elegido a {@link WebhookPagoService}, que sigue siendo el único que confirma. Sin {@code @Transactional}:
 * {@code recibir} abre la suya. Inyecta la clase concreta a propósito: sin la simulada, este bean no arranca (DT-17).
 */
@Service
@ConditionalOnProperty(name = "espacioya.pagos.pasarela", havingValue = "simulada")
public class SimulacionPago {

    public static final String ACCION_PAGO_SIMULADO = "PAGO_SIMULADO";

    private static final String ENTIDAD_RESERVA = "reserva";
    private static final String ID_NOTIFICADO = "data.id";
    private static final long INTENTO_APROBADO = 1;

    private final EvaluacionCobro evaluacion;
    private final SesionesCheckout sesiones;
    private final PasarelaSimulada pasarela;
    private final WebhookPagoService webhook;
    private final PagoRepository pagos;
    private final AuditoriaService auditoria;

    public SimulacionPago(EvaluacionCobro evaluacion, SesionesCheckout sesiones, PasarelaSimulada pasarela,
                          WebhookPagoService webhook, PagoRepository pagos, AuditoriaService auditoria) {
        this.evaluacion = evaluacion;
        this.sesiones = sesiones;
        this.pasarela = pasarela;
        this.webhook = webhook;
        this.pagos = pagos;
        this.auditoria = auditoria;
    }

    /** Solo lee (R61). */
    public SimulacionDTO consultar(UsuarioAutenticado usuario, String reservaId) {
        evaluacion.exigirRol(usuario);
        var resumen = evaluacion.resumen(usuario, reservaId);
        return new SimulacionDTO(resumen.pagoDisponible() && sesiones.existe(resumen.reservaId()));
    }

    public void simular(UsuarioAutenticado usuario, String reservaId, ResultadoPasarela resultado, String ip) {
        var reserva = evaluacion.exigirCheckoutNoAgotado(usuario, reservaId);
        if (!sesiones.existe(reserva.id())) {
            throw new ReglaNegocioException(EvaluacionCobro.MENSAJE_PAGO_NO_DISPONIBLE);
        }
        var notificacion = pasarela.notificacionDe(reserva.id(), reserva.montoTotal(), resultado,
                intento(reserva.id(), resultado));
        // Antes de recibir: R52 y R53 auditan sin usuario, y esto deja rastro de quién decidió aunque recibir falle.
        var detalle = "resultado=" + resultado.name() + ";transaccion=" + notificacion.parametros().get(ID_NOTIFICADO);
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_PAGO_SIMULADO, ENTIDAD_RESERVA,
                reserva.id(), ip, detalle));
        webhook.recibir(notificacion, ip);
    }

    // Aprobar es siempre el mismo id, para que dos envíos sean el mismo pago (R68, R69). Con datos de la base y no de
    // memoria, un reinicio no repite el id de un rechazo (R70).
    private long intento(Long reservaId, ResultadoPasarela resultado) {
        if (resultado == ResultadoPasarela.APROBADO) {
            return INTENTO_APROBADO;
        }
        return pagos.countByReservaIdAndEstado(reservaId, EstadoPago.RECHAZADO) + 1;
    }
}
