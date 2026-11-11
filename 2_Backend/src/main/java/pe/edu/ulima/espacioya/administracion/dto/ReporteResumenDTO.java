package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Fila de la cola de moderación (HU-27 R20, R21). Sin datos del reportante (P14). */
public record ReporteResumenDTO(Long id, String tipoObjetivo, Long objetivoId, String espacioNombre, String motivo,
                                String estado, Instant creadoEn, Instant resueltoEn) {
}
