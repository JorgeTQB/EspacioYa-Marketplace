package pe.edu.ulima.espacioya.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una reserva liquidada (HU-19 R36, R42). Sin datos del usuario que reservó ni del pago (R44). {@code cancelada}: es
 * una cancelada por el usuario sin reembolso (D3).
 */
public record LineaLiquidacionDTO(Long reservaId, String espacioNombre, LocalDate fecha, Integer horaInicio,
                                  Integer horaFin, BigDecimal monto, boolean simulada, boolean cancelada) {
}
