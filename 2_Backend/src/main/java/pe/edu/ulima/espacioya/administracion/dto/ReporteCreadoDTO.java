package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Respuesta 201 al reportar (HU-27 R3). Sin datos del reportante ni del contenido. */
public record ReporteCreadoDTO(Long id, String tipoObjetivo, Long objetivoId, String motivo, String estado,
                               Instant creadoEn) {
}
