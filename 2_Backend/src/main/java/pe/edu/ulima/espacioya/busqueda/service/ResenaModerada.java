package pe.edu.ulima.espacioya.busqueda.service;

import java.time.LocalDate;

/** Una reseña en su estado actual, tal como la revisa el admin (HU-27 R25). Sin datos de su autor. */
public record ResenaModerada(Long id, Long espacioId, int calificacion, String comentario, LocalDate fecha,
                             String estado) {
}
