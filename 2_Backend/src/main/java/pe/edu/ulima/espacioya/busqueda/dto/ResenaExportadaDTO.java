package pe.edu.ulima.espacioya.busqueda.dto;

import java.time.LocalDate;

/** Una reseña en la sección {@code resenas} de la exportación de HU-22. */
public record ResenaExportadaDTO(Long reservaId, String espacioNombre, int calificacion, String comentario,
                                 String estado, LocalDate fecha) {
}
