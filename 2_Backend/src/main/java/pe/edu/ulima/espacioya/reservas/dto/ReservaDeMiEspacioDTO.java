package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Reserva de un espacio vista por su arrendador (HU-14 CA2): sin usuarioId, correo, teléfono ni pago (R20). */
public record ReservaDeMiEspacioDTO(Long id, Long espacioId, String espacioNombre, String usuarioNombre,
                                    String estado, LocalDate fecha, int horaInicio, int horaFin,
                                    BigDecimal montoTotal) {
}
