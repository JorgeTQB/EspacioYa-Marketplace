package pe.edu.ulima.espacioya.pagos.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.OrigenReembolso;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.PagoDeReserva;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoDePago;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.pagos.service.reembolso.Disparo;
import pe.edu.ulima.espacioya.pagos.service.reembolso.EjecutorReembolsos;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudesReembolso;

/**
 * Client del Command de HU-16 al cancelar: crea el reembolso y entrega el comando al Invoker para después del commit.
 * No llama a la pasarela (design § 7).
 */
@Service
public class PagoServiceImpl implements PagoService {

    public static final String ACCION_REEMBOLSO_SOLICITADO = "REEMBOLSO_SOLICITADO";

    private static final String ENTIDAD_PAGO = "pago";

    private final PagoRepository pagos;
    private final ReembolsoRepository reembolsos;
    private final SolicitudesReembolso solicitudes;
    private final EjecutorReembolsos ejecutor;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public PagoServiceImpl(PagoRepository pagos, ReembolsoRepository reembolsos, SolicitudesReembolso solicitudes,
                           EjecutorReembolsos ejecutor, AuditoriaService auditoria, Clock reloj) {
        this.pagos = pagos;
        this.reembolsos = reembolsos;
        this.solicitudes = solicitudes;
        this.ejecutor = ejecutor;
        this.auditoria = auditoria;
        this.reloj = reloj;
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
        if (reembolsos.existsByPagoIdAndOrigen(pago.getId(), OrigenReembolso.CANCELACION)) {
            throw new IllegalStateException("El pago " + pago.getId() + " ya tiene un reembolso por cancelación");
        }
        // saveAndFlush: si uq_reembolso_pago_origen salta, revierte la cancelación aquí y no después (R19, R20).
        var reembolso = reembolsos.saveAndFlush(Reembolso.porCancelacion(pago.getId(), monto, Instant.now(reloj)));
        auditoria.registrar(new EventoAuditoria(solicitud.solicitadoPor(), ACCION_REEMBOLSO_SOLICITADO, ENTIDAD_PAGO,
                pago.getId(), solicitud.ip(),
                "origen=CANCELACION;reservaId=" + solicitud.reservaId() + ";monto=" + monto.toPlainString()));
        ejecutor.ejecutarDespuesDelCommit(solicitudes.crear(reembolso.getId(), Disparo.AUTOMATICO,
                solicitud.solicitadoPor(), solicitud.ip()));
    }

    // HU-22 R6, R13: dos consultas por lote; nunca ultimos4 ni ids de la pasarela.
    @Override
    @Transactional(readOnly = true)
    public List<PagoDeReserva> pagosDeReservas(Collection<Long> reservaIds) {
        if (reservaIds == null || reservaIds.isEmpty()) {
            return List.of();
        }
        var deLasReservas = pagos.findByReservaIdInOrderByCreadoEnAscIdAsc(reservaIds);
        if (deLasReservas.isEmpty()) {
            return List.of();
        }
        var pagoIds = deLasReservas.stream().map(Pago::getId).toList();
        var porPago = reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(pagoIds).stream()
                .collect(Collectors.groupingBy(Reembolso::getPagoId, Collectors.mapping(
                        r -> new ReembolsoDePago(r.getMonto(), r.getEstado().name(), r.getCreadoEn()),
                        Collectors.toList())));
        return deLasReservas.stream()
                .map(p -> new PagoDeReserva(p.getReservaId(), p.getMonto(), p.getEstado().name(), p.getCreadoEn(),
                        List.copyOf(porPago.getOrDefault(p.getId(), List.of()))))
                .toList();
    }
}
