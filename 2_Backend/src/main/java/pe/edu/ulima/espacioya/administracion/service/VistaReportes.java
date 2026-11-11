package pe.edu.ulima.espacioya.administracion.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReporteAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.EspacioReportadoDTO;
import pe.edu.ulima.espacioya.administracion.dto.ResenaReportadaDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReporteResumenDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.busqueda.service.ResenaModerada;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.ModeracionEspacios;

/**
 * Arma los DTO de reportes para el admin (HU-27 § 7). Se une a la transacción del servicio que lo llama. Los nombres
 * se piden por lote para toda la página: sin N+1. Nunca devuelve datos del reportante ni el correo o el teléfono del
 * arrendador (P14, RNF-SEG-16).
 */
@Component
public class VistaReportes {

    private final ReporteRepository reportes;
    private final ModeracionEspacios espacios;
    private final ModeracionResenas resenas;
    private final CatalogoEspaciosService catalogo;
    private final CuentaService cuentas;

    public VistaReportes(ReporteRepository reportes, ModeracionEspacios espacios, ModeracionResenas resenas,
                         CatalogoEspaciosService catalogo, CuentaService cuentas) {
        this.reportes = reportes;
        this.espacios = espacios;
        this.resenas = resenas;
        this.catalogo = catalogo;
        this.cuentas = cuentas;
    }

    public List<ReporteResumenDTO> resumenes(List<Reporte> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        var resenaIds = lista.stream().map(Reporte::getResenaId).filter(Objects::nonNull).toList();
        Map<Long, ResenaModerada> descritas = resenaIds.isEmpty() ? Map.of() : resenas.describir(resenaIds);
        var espacioIds = new HashSet<Long>();
        lista.stream().map(Reporte::getEspacioId).filter(Objects::nonNull).forEach(espacioIds::add);
        descritas.values().forEach(resena -> espacioIds.add(resena.espacioId()));
        var nombres = catalogo.nombres(espacioIds);
        return lista.stream().map(reporte -> {
            var espacioId = espacioDe(reporte, descritas);
            return new ReporteResumenDTO(reporte.getId(), reporte.getTipoObjetivo().name(), reporte.getObjetivoId(),
                    espacioId == null ? null : nombres.get(espacioId), reporte.getMotivo().name(),
                    reporte.getEstado().name(), reporte.getCreadoEn(), reporte.getResueltoEn());
        }).toList();
    }

    /** {@code reservasCanceladas}: {@code null} en la consulta; la cantidad en la respuesta de la decisión (R45). */
    public DetalleReporteAdminDTO detalle(Reporte reporte, Integer reservasCanceladas) {
        EspacioReportadoDTO espacio = null;
        ResenaReportadaDTO resena = null;
        long pendientes;
        if (reporte.esDeEspacio()) {
            pendientes = reportes.countByEspacioIdAndEstado(reporte.getEspacioId(), EstadoReporte.POR_REVISAR);
            espacio = espacioReportado(reporte.getEspacioId());
        } else {
            pendientes = reportes.countByResenaIdAndEstado(reporte.getResenaId(), EstadoReporte.POR_REVISAR);
            resena = resenaReportada(reporte.getResenaId());
        }
        return new DetalleReporteAdminDTO(reporte.getId(), reporte.getTipoObjetivo().name(), reporte.getObjetivoId(),
                reporte.getMotivo().name(), reporte.getEstado().name(), reporte.getCreadoEn(),
                reporte.decision().map(Enum::name).orElse(null), reporte.getMotivoDecision(),
                reporte.getResueltoEn(), pendientes, espacio, resena, reservasCanceladas);
    }

    private EspacioReportadoDTO espacioReportado(Long espacioId) {
        var moderado = espacios.describir(List.of(espacioId)).get(espacioId);
        if (moderado == null) {
            return null;
        }
        var arrendador = cuentas.nombres(List.of(moderado.arrendadorId())).get(moderado.arrendadorId());
        return new EspacioReportadoDTO(moderado.nombre(), moderado.tipo(), moderado.zona(), moderado.direccion(),
                moderado.capacidad(), moderado.precioHora(), moderado.descripcion(), moderado.estado(), arrendador);
    }

    private ResenaReportadaDTO resenaReportada(Long resenaId) {
        var moderada = resenas.describir(List.of(resenaId)).get(resenaId);
        if (moderada == null) {
            return null;
        }
        var nombre = catalogo.nombres(List.of(moderada.espacioId())).get(moderada.espacioId());
        return new ResenaReportadaDTO(moderada.calificacion(), moderada.comentario(), moderada.fecha(),
                moderada.estado(), moderada.espacioId(), nombre);
    }

    private static Long espacioDe(Reporte reporte, Map<Long, ResenaModerada> descritas) {
        if (reporte.esDeEspacio()) {
            return reporte.getEspacioId();
        }
        var resena = descritas.get(reporte.getResenaId());
        return resena == null ? null : resena.espacioId();
    }
}
