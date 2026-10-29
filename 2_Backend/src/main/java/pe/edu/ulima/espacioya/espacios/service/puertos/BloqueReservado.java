package pe.edu.ulima.espacioya.espacios.service.puertos;

import java.time.LocalDate;

/** Un bloque de 1 hora retenido por una reserva activa; {@code pagado} si la reserva está CONFIRMADA. */
public record BloqueReservado(LocalDate fecha, int horaInicio, boolean pagado) {
}
