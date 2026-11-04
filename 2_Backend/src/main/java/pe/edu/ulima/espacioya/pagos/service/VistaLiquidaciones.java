package pe.edu.ulima.espacioya.pagos.service;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.domain.AvisoArrendador;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoCuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;
import pe.edu.ulima.espacioya.pagos.dto.CuentaDestinoDTO;
import pe.edu.ulima.espacioya.pagos.dto.LineaLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDetalleDTO;
import pe.edu.ulima.espacioya.pagos.dto.MiLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.PagoLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

/**
 * Arma los DTO de las liquidaciones (HU-19 DT-13): las líneas de toda la página en una consulta, sus reservas en una
 * llamada al puerto, los nombres y los avisos por lote (sin N+1). El número completo de la cuenta solo sale si el
 * llamador lo pide (D4, R38, R40). No exige rol: lo llaman servicios que ya lo validaron.
 */
@Component
public class VistaLiquidaciones {

    public static final String NOMBRE_POR_DEFECTO = "Arrendador #%d";

    private final LiquidacionDetalleRepository detalles;
    private final ReservasLiquidables reservas;
    private final CuentaService cuentasUsuario;

    public VistaLiquidaciones(LiquidacionDetalleRepository detalles, ReservasLiquidables reservas,
                              CuentaService cuentasUsuario) {
        this.detalles = detalles;
        this.reservas = reservas;
        this.cuentasUsuario = cuentasUsuario;
    }

    /** Listas del admin y respuesta de la generación (R19, R33, R34, R37): sin líneas ni cuenta. */
    public List<LiquidacionAdminDTO> resumenes(List<Liquidacion> liquidaciones) {
        if (liquidaciones.isEmpty()) {
            return List.of();
        }
        var lineas = lineasDe(liquidaciones);
        var arrendadores = arrendadoresDe(liquidaciones);
        var nombres = cuentasUsuario.nombres(arrendadores);
        var avisos = avisosDe(arrendadores);
        return liquidaciones.stream()
                .map(l -> new LiquidacionAdminDTO(l.getId(), l.getArrendadorId(),
                        nombre(nombres, l.getArrendadorId()), l.getPeriodo(), l.getGeneradaEn(), l.getEstado().name(),
                        l.getMontoBruto(), l.getComision(), l.getMontoNeto(),
                        lineas.getOrDefault(l.getId(), List.of()).size(), l.isSimulada(),
                        avisos.get(l.getArrendadorId()), l.getNumeroOperacion(), l.getPagadaEn()))
                .toList();
    }

    /**
     * Detalle del admin (R36, R37). {@code conNumeroCompleto} solo cuando la liquidación es pagable y el llamador ya
     * auditó la consulta (R38, R39); en cualquier otro caso el número va solo enmascarado (R40).
     */
    public LiquidacionAdminDetalleDTO detalle(Liquidacion liquidacion, Optional<CuentaBancaria> cuenta,
                                              boolean conNumeroCompleto, Instant ahora) {
        var lineas = lineasDe(List.of(liquidacion)).getOrDefault(liquidacion.getId(), List.of());
        var descritas = describir(lineas);
        var arrendadorId = liquidacion.getArrendadorId();
        var nombres = cuentasUsuario.nombres(Set.of(arrendadorId));
        var avisos = avisosDe(Set.of(arrendadorId)).get(arrendadorId);
        var pagable = liquidacion.motivoNoPagable(cuenta, ahora).isEmpty();
        return new LiquidacionAdminDetalleDTO(liquidacion.getId(), arrendadorId, nombre(nombres, arrendadorId),
                liquidacion.getPeriodo(), liquidacion.getGeneradaEn(), liquidacion.getEstado().name(),
                liquidacion.getMontoBruto(), liquidacion.getComision(), liquidacion.getMontoNeto(),
                liquidacion.isSimulada(), avisos, pagable,
                cuenta.map(c -> cuentaDestino(c, conNumeroCompleto && pagable, ahora)).orElse(null),
                pagoDe(liquidacion), lineas.stream().map(linea -> linea(linea, descritas)).toList());
    }

    /** "Mis liquidaciones" (R42, R44): sin el usuario que reservó, sin datos del pago ni del admin. */
    public List<MiLiquidacionDTO> misLiquidaciones(List<Liquidacion> liquidaciones) {
        if (liquidaciones.isEmpty()) {
            return List.of();
        }
        var lineas = lineasDe(liquidaciones);
        var descritas = describir(lineas.values().stream().flatMap(List::stream).toList());
        return liquidaciones.stream()
                .map(l -> new MiLiquidacionDTO(l.getId(), l.getPeriodo(), l.getGeneradaEn(), l.getEstado().name(),
                        l.getMontoBruto(), l.getComision(), l.getMontoNeto(), l.isSimulada(), pagoDe(l),
                        lineas.getOrDefault(l.getId(), List.of()).stream()
                                .map(linea -> linea(linea, descritas)).toList()))
                .toList();
    }

    private Map<Long, List<LiquidacionDetalle>> lineasDe(List<Liquidacion> liquidaciones) {
        var ids = liquidaciones.stream().map(Liquidacion::getId).toList();
        return detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(ids).stream()
                .collect(Collectors.groupingBy(LiquidacionDetalle::getLiquidacionId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Map<Long, ReservaLiquidable> describir(List<LiquidacionDetalle> lineas) {
        if (lineas.isEmpty()) {
            return Map.of();
        }
        return reservas.describir(lineas.stream().map(LiquidacionDetalle::getReservaId).toList()).stream()
                .collect(Collectors.toMap(ReservaLiquidable::reservaId, Function.identity()));
    }

    private static Set<Long> arrendadoresDe(List<Liquidacion> liquidaciones) {
        return liquidaciones.stream().map(Liquidacion::getArrendadorId).collect(Collectors.toSet());
    }

    // D6, S12: se calculan al leer, en dos llamadas por lote.
    private Map<Long, List<String>> avisosDe(Collection<Long> arrendadores) {
        var noActivos = cuentasUsuario.arrendadoresNoActivos();
        var sinIdentidad = cuentasUsuario.arrendadoresSinIdentidadVerificada(arrendadores);
        return arrendadores.stream().collect(Collectors.toMap(Function.identity(),
                id -> AvisoArrendador.de(noActivos.contains(id), sinIdentidad.contains(id)).stream()
                        .map(Enum::name).toList()));
    }

    private static String nombre(Map<Long, String> nombres, Long arrendadorId) {
        return nombres.getOrDefault(arrendadorId, String.format(NOMBRE_POR_DEFECTO, arrendadorId));
    }

    // Una línea cuya reserva no describe el puerto no debería existir; si pasara, sale sin espacio ni horario.
    private static LineaLiquidacionDTO linea(LiquidacionDetalle linea, Map<Long, ReservaLiquidable> descritas) {
        var reserva = descritas.get(linea.getReservaId());
        if (reserva == null) {
            return new LineaLiquidacionDTO(linea.getReservaId(), null, null, null, null, linea.getMonto(),
                    linea.isSimulado(), false);
        }
        return new LineaLiquidacionDTO(linea.getReservaId(), reserva.nombreEspacio(), reserva.fecha(),
                reserva.horaInicio(), reserva.horaFin(), linea.getMonto(), linea.isSimulado(), reserva.cancelada());
    }

    private static PagoLiquidacionDTO pagoDe(Liquidacion liquidacion) {
        if (liquidacion.getEstado() != EstadoLiquidacion.PAGADA) {
            return null;
        }
        return new PagoLiquidacionDTO(liquidacion.getNumeroOperacion(), liquidacion.getPagadaEn(),
                liquidacion.getBancoDestino(), liquidacion.getCuentaDestino());
    }

    private static CuentaDestinoDTO cuentaDestino(CuentaBancaria cuenta, boolean conNumeroCompleto, Instant ahora) {
        var estado = cuenta.estado(ahora);
        return new CuentaDestinoDTO(cuenta.getBanco(), cuenta.getTitular(), cuenta.enmascarado(),
                conNumeroCompleto ? cuenta.numeroParaTransferir() : null, estado.name(),
                estado == EstadoCuentaBancaria.RETENIDA ? cuenta.getRetenerHasta() : null);
    }
}
