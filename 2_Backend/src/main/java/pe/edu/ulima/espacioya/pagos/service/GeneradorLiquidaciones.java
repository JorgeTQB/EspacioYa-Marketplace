package pe.edu.ulima.espacioya.pagos.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.domain.AvisoArrendador;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.ReglasLiquidacion;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

/**
 * Selección y armado de las liquidaciones de una generación (HU-19 CA1, CA3, D1). {@code pagos} decide qué pago cuenta
 * y la regla de las 48 h; {@code reservas}, por el puerto, si el estado de la reserva admite liquidación y su fin
 * (DT-3). Todo con <b>un</b> corte tomado después del bloqueo de generación (DT-7). Nunca modifica una liquidación
 * existente: las reservas nuevas van a una liquidación nueva (R17).
 */
@Component
public class GeneradorLiquidaciones {

    private static final String PASARELA_SIMULADA = "simulada";
    private static final String ENTIDAD_LIQUIDACION = "liquidacion";

    private final LiquidacionRepository liquidaciones;
    private final LiquidacionDetalleRepository detalles;
    private final PagoRepository pagos;
    private final CuentaBancariaRepository cuentas;
    private final ReservasLiquidables reservas;
    private final CuentaService cuentasUsuario;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final boolean incluirSimulados;

    public GeneradorLiquidaciones(LiquidacionRepository liquidaciones, LiquidacionDetalleRepository detalles,
                                  PagoRepository pagos, CuentaBancariaRepository cuentas,
                                  ReservasLiquidables reservas, CuentaService cuentasUsuario,
                                  AuditoriaService auditoria, Clock reloj,
                                  @Value("${espacioya.pagos.pasarela}") String pasarela) {
        this.liquidaciones = liquidaciones;
        this.detalles = detalles;
        this.pagos = pagos;
        this.cuentas = cuentas;
        this.reservas = reservas;
        this.cuentasUsuario = cuentasUsuario;
        this.auditoria = auditoria;
        this.reloj = reloj;
        // Como @ConditionalOnProperty, sin distinguir mayúsculas (DT-6, D2).
        this.incluirSimulados = PASARELA_SIMULADA.equalsIgnoreCase(pasarela == null ? "" : pasarela.strip());
    }

    /** Lo que creó una generación y el corte que usó. {@code creadas} vacía si no había nada que liquidar (R18). */
    public record Generacion(Instant corte, List<Liquidacion> creadas) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Generacion generar(Long adminId, String ip) {
        liquidaciones.bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION);
        // Después del bloqueo: una segunda generación ve lo que la primera ya confirmó (R22).
        var corte = Instant.now(reloj);
        var pendientes = pagos.pagadosSinLiquidar(incluirSimulados);
        if (pendientes.isEmpty()) {
            return new Generacion(corte, List.of());
        }
        var porArrendador = liquidablesPorArrendador(pendientes, corte);
        if (porArrendador.isEmpty()) {
            return new Generacion(corte, List.of());
        }
        // Los avisos no filtran nada (D6, R24): solo quedan en la auditoría de este momento.
        var noActivos = cuentasUsuario.arrendadoresNoActivos();
        var sinIdentidad = cuentasUsuario.arrendadoresSinIdentidadVerificada(porArrendador.keySet());
        var creadas = new ArrayList<Liquidacion>();
        porArrendador.forEach((arrendadorId, suyos) -> creadas.add(crear(arrendadorId, suyos, corte, adminId, ip,
                AvisoArrendador.de(noActivos.contains(arrendadorId), sinIdentidad.contains(arrendadorId)))));
        return new Generacion(corte, List.copyOf(creadas));
    }

    // TreeMap: una liquidación por arrendador en orden estable de id; sus pagos siguen por reserva ascendente.
    private Map<Long, List<Pago>> liquidablesPorArrendador(List<Pago> pendientes, Instant corte) {
        Map<Long, ReservaLiquidable> descritas = reservas.describir(pendientes.stream().map(Pago::getReservaId)
                        .toList()).stream()
                .collect(Collectors.toMap(ReservaLiquidable::reservaId, Function.identity()));
        Map<Long, List<Pago>> porArrendador = new TreeMap<>();
        for (var pago : pendientes) {
            var reserva = descritas.get(pago.getReservaId());
            if (reserva != null && reserva.admitida() && ReglasLiquidacion.esLiquidable(reserva.finEn(), corte)) {
                porArrendador.computeIfAbsent(reserva.arrendadorId(), id -> new ArrayList<>()).add(pago);
            }
        }
        return porArrendador;
    }

    private Liquidacion crear(Long arrendadorId, List<Pago> suyos, Instant corte, Long adminId, String ip,
                              List<AvisoArrendador> avisos) {
        var conCuenta = cuentas.findByArrendadorId(arrendadorId).isPresent();
        var simulada = suyos.stream().anyMatch(pago -> ReglasLiquidacion.esSimulado(pago.getIdTransaccionPasarela()));
        var liquidacion = liquidaciones.saveAndFlush(Liquidacion.generar(arrendadorId,
                suyos.stream().map(Pago::getMonto).toList(), conCuenta, simulada, corte, adminId));
        // Aquí salta uq_liquidacion_detalle_reserva si otra generación se cruzó pese al bloqueo (DT-7).
        detalles.saveAllAndFlush(suyos.stream()
                .map(pago -> LiquidacionDetalle.de(liquidacion.getId(), pago.getReservaId(), pago.getId(),
                        pago.getMonto(), ReglasLiquidacion.esSimulado(pago.getIdTransaccionPasarela())))
                .toList());
        auditoria.registrar(new EventoAuditoria(adminId, LiquidacionAdminService.ACCION_GENERADA, ENTIDAD_LIQUIDACION,
                liquidacion.getId(), ip, detalle(liquidacion, suyos.size(), avisos)));
        return liquidacion;
    }

    private static String detalle(Liquidacion liquidacion, int reservas, List<AvisoArrendador> avisos) {
        var texto = "arrendadorId=" + liquidacion.getArrendadorId() + ";reservas=" + reservas
                + ";bruto=" + liquidacion.getMontoBruto().toPlainString()
                + ";comision=" + liquidacion.getComision().toPlainString()
                + ";neto=" + liquidacion.getMontoNeto().toPlainString()
                + ";estado=" + liquidacion.getEstado().name() + ";periodo=" + liquidacion.getPeriodo()
                + ";simulada=" + liquidacion.isSimulada();
        if (avisos.isEmpty()) {
            return texto;
        }
        return texto + ";avisos=" + avisos.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
