package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Reserva vista por su dueño (HU-12). Sin {@code usuarioId} ni datos del arrendador (R41). */
public record ReservaDTO(Long id, Long espacioId, String espacioNombre, String estado, LocalDate fecha,
                         int horaInicio, int horaFin, BigDecimal precioHora, BigDecimal montoTotal,
                         Instant expiraEn, long segundosRestantes) {
}
