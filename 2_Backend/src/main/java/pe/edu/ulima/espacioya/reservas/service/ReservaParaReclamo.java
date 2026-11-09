package pe.edu.ulima.espacioya.reservas.service;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lo que {@code administracion} necesita saber de una reserva para un reclamo (HU-26 DT-2). {@code confirmada} sale del
 * estado leído con {@code estadoEn}; {@code inicioEn} y {@code finEn} son el inicio del primer bloque y el fin del
 * último en Lima, contando todas las filas de {@code reserva_bloque}.
 */
public record ReservaParaReclamo(Long id, Long usuarioId, Long espacioId, String espacioNombre, Long arrendadorId,
                                 boolean confirmada, LocalDate fecha, int horaInicio, int horaFin, Instant inicioEn,
                                 Instant finEn) {
}
