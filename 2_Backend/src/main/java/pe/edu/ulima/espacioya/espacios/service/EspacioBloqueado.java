package pe.edu.ulima.espacioya.espacios.service;

/** Datos del espacio leídos bajo su bloqueo de fila, para confirmar una reserva (HU-13). */
public record EspacioBloqueado(Long arrendadorId, String nombre) {
}
