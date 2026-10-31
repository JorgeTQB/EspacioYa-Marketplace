package pe.edu.ulima.espacioya.pagos.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;

/** HU-15: solo registra la solicitud de reembolso; HU-16 la ejecuta contra la pasarela (design § 11.1). */
@Service
public class PagoServiceImpl implements PagoService {

    public static final String ACCION_REEMBOLSO_SOLICITADO = "REEMBOLSO_SOLICITADO";

    private static final String ENTIDAD_PAGO = "pago";

    private final PagoRepository pagos;
    private final AuditoriaService auditoria;

    public PagoServiceImpl(PagoRepository pagos, AuditoriaService auditoria) {
        this.pagos = pagos;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reembolsarCancelacion(ReembolsoPorCancelacion solicitud) {
        var pago = pagos.findByReservaIdAndEstado(solicitud.reservaId(), EstadoPago.PAGADO)
                .orElseThrow(() -> new IllegalStateException(
                        "La reserva " + solicitud.reservaId() + " no tiene un pago PAGADO que reembolsar"));
        var monto = solicitud.monto();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.compareTo(pago.getMonto()) > 0) {
            throw new IllegalArgumentException("El monto a reembolsar debe ser mayor que 0 y no superar el pago");
        }
        auditoria.registrar(new EventoAuditoria(solicitud.solicitadoPor(), ACCION_REEMBOLSO_SOLICITADO, ENTIDAD_PAGO,
                pago.getId(), solicitud.ip(),
                "origen=CANCELACION;reservaId=" + solicitud.reservaId() + ";monto=" + monto.toPlainString()));
    }
}
