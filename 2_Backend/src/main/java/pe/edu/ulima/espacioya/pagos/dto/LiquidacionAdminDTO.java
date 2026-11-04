package pe.edu.ulima.espacioya.pagos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Una liquidación en las listas del admin y en la respuesta de la generación (HU-19 R19, R33, R34, R37). Nunca el
 * número de cuenta (R40). {@code numeroOperacion} y {@code pagadaEn} solo si está PAGADA.
 */
public record LiquidacionAdminDTO(Long id, Long arrendadorId, String arrendadorNombre, LocalDate periodo,
                                  Instant generadaEn, String estado, BigDecimal montoBruto, BigDecimal comision,
                                  BigDecimal montoNeto, int reservas, boolean simulada, List<String> avisos,
                                  String numeroOperacion, Instant pagadaEn) {
}
