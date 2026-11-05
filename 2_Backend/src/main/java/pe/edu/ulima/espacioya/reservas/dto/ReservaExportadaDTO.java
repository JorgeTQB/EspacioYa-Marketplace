package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una reserva en la exportación de HU-22 (R5, R7). Para el arrendador van solo las {@code CONFIRMADA} de sus espacios,
 * sin {@code precioHora}, {@code creadaEn} ni datos de cancelación, y nunca datos de quien reservó (R13).
 */
public record ReservaExportadaDTO(Long id, String espacio, LocalDate fecha, int horaInicio, int horaFin, String estado,
                                  BigDecimal precioHora, BigDecimal montoTotal, Instant creadaEn, String canceladaPor,
                                  Instant canceladaEn, BigDecimal montoReembolso) {
}
