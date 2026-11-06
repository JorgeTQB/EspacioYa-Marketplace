package pe.edu.ulima.espacioya.busqueda.dto;

import java.time.LocalDate;

/** Respuesta de {@code POST /api/resenas}. {@code fecha} es el día de creación en Lima. */
public record ResenaCreadaDTO(Long id, Long reservaId, int calificacion, String comentario, LocalDate fecha) {
}
