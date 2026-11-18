package pe.edu.ulima.espacioya.administracion.dto;

import java.time.LocalDate;

/** La reseña de un reporte en su estado actual (HU-27 R25). Sin datos de su autor. */
public record ResenaReportadaDTO(int calificacion, String comentario, LocalDate fecha, String estado, Long espacioId,
                                 String espacioNombre) {
}
