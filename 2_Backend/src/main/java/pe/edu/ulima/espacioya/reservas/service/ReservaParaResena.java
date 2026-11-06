package pe.edu.ulima.espacioya.reservas.service;

import java.time.Instant;

/**
 * Lo que {@code busqueda} necesita saber de una reserva para reseñarla (HU-24). {@code confirmada} sale del estado
 * leído con {@code estadoEn} y {@code finEn} es el fin del último bloque en Lima.
 */
public record ReservaParaResena(Long id, Long espacioId, boolean confirmada, Instant finEn) {
}
