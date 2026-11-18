package pe.edu.ulima.espacioya.espacios.dto;

/** Respuesta de la pausa y la baja del admin (HU-21 DT-9): solo el id y el estado, sin la ficha ni las fotos. */
public record EstadoEspacioDTO(Long id, String estado) {
}
