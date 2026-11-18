package pe.edu.ulima.espacioya.busqueda.dto;

import java.time.LocalDate;

/** Una reseña en la ficha pública: nada del autor, de la reserva ni el estado (R31, RNF-SEG-16). */
public record ResenaPublicaDTO(Long id, int calificacion, String comentario, LocalDate fecha) {
}
