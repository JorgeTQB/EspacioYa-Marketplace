package pe.edu.ulima.espacioya.pagos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Una liquidación vista por su arrendador (HU-19 R42). Sin el usuario que reservó, sin datos del pago ni el admin que
 * generó o pagó (R44).
 */
public record MiLiquidacionDTO(Long id, LocalDate periodo, Instant generadaEn, String estado, BigDecimal montoBruto,
                               BigDecimal comision, BigDecimal montoNeto, boolean simulada, PagoLiquidacionDTO pago,
                               List<LineaLiquidacionDTO> reservas) {
}
