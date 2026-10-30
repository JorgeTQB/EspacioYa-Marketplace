package pe.edu.ulima.espacioya.reservas.repository;

import java.time.LocalDate;

import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;

/** Bloque retenido por una reserva activa, con el estado de la reserva que lo retiene. */
public record BloqueActivo(Long espacioId, LocalDate fecha, short horaInicio, EstadoReserva estado) {
}
