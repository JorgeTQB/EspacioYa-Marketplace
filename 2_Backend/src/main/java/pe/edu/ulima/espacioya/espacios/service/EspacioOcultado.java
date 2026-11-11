package pe.edu.ulima.espacioya.espacios.service;

/** Resultado de ocultar un espacio por moderación (HU-27): lo que necesita el correo al arrendador. */
public record EspacioOcultado(Long id, Long arrendadorId, String nombre, String estadoAnterior) {
}
