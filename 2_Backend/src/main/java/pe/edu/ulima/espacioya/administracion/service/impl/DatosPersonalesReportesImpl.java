package pe.edu.ulima.espacioya.administracion.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.dto.ReporteExportadoDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;

/**
 * Participante de los reportes en HU-22 (HU-27 P12, § 11). Exporta los reportes que hizo el titular, sin la decisión
 * del admin. No impide eliminar la cuenta y no anonimiza nada: los reportes no tienen texto libre y
 * {@code reportante_id} queda apuntando a la cuenta ya anonimizada (R60, R61).
 */
@Component
@Order(5)
public class DatosPersonalesReportesImpl implements ParticipanteDatosPersonales {

    public static final String SECCION_REPORTES = "reportes";

    private final ReporteRepository reportes;

    public DatosPersonalesReportesImpl(ReporteRepository reportes) {
        this.reportes = reportes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeccionDatos> exportar(TitularDatos titular) {
        if (!titular.esUsuario() && !titular.esArrendador()) {
            return List.of();
        }
        var exportados = reportes.findByReportanteIdOrderByCreadoEnAscIdAsc(titular.usuarioId()).stream()
                .map(reporte -> new ReporteExportadoDTO(reporte.getTipoObjetivo().name(), reporte.getObjetivoId(),
                        reporte.getMotivo().name(), reporte.getEstado().name(), reporte.getCreadoEn(),
                        reporte.getResueltoEn()))
                .toList();
        return List.of(new SeccionDatos(SECCION_REPORTES, exportados));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Impedimento> impedimentos(TitularDatos titular, Instant ahora) {
        return List.of();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void anonimizar(TitularDatos titular) {
        // R61: nada que reemplazar; la auditoría CUENTA_ELIMINADA ya registra la eliminación.
    }
}
