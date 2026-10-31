package pe.edu.ulima.espacioya.reservas.repository;

import java.time.LocalDate;

/** Horario de una reserva sobre todas sus filas de {@code reserva_bloque}, activas o no (HU-14 R8). */
public record HorarioReserva(Long reservaId, LocalDate fecha, short horaInicio, short horaUltima) {
}
