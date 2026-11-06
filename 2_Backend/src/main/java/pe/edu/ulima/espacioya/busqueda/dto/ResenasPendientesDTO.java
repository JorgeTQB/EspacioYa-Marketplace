package pe.edu.ulima.espacioya.busqueda.dto;

import java.util.List;

/** Las reservas que el usuario de la sesión puede reseñar, en orden ascendente (P5). */
public record ResenasPendientesDTO(List<Long> reservaIds) {
}
