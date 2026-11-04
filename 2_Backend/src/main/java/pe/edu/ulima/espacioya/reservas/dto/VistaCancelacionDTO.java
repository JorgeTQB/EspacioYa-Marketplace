package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo que se reembolsaría si se cancela ahora (HU-15 CA2). Calculado en el servidor (R30). */
public record VistaCancelacionDTO(Long reservaId, String espacioNombre, LocalDate fecha, int horaInicio, int horaFin,
                                  BigDecimal montoPagado, String politica, int porcentaje, BigDecimal montoReembolso) {
}
