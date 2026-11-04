package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Una reserva vista por la liquidación (HU-19). {@code admitida}: su estado admite liquidación ({@code CONFIRMADA}, o
 * {@code CANCELADA} por el usuario con {@code monto_reembolso = 0}, D3). {@code cancelada}: su estado es
 * {@code CANCELADA}. {@code finEn}: fin del último bloque en America/Lima como instante (S1). {@code horaFin} va de 1 a
 * 24.
 */
public record ReservaLiquidable(Long reservaId, Long arrendadorId, Long espacioId, String nombreEspacio,
                                LocalDate fecha, int horaInicio, int horaFin, Instant finEn,
                                boolean admitida, boolean cancelada) {
}
