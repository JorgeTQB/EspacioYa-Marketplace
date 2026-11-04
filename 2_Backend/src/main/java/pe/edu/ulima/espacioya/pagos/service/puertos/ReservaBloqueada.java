package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Reserva leída bajo sus bloqueos, con lo necesario para clasificar el pago y escribir los correos (HU-13 DT-7). */
public record ReservaBloqueada(Long id, Long usuarioId, Long arrendadorId, String espacioNombre, LocalDate fecha,
                               int horaInicio, int horaFin, BigDecimal montoTotal, boolean vigente,
                               Instant evaluadaEn) {
}
