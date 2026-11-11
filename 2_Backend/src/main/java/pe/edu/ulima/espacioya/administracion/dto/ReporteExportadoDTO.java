package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Un reporte que hizo el titular, en la exportación de HU-22 (HU-27 R59). Sin el motivo ni el admin de la decisión. */
public record ReporteExportadoDTO(String tipoObjetivo, Long objetivoId, String motivo, String estado,
                                  Instant creadoEn, Instant resueltoEn) {
}
