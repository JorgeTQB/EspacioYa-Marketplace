package pe.edu.ulima.espacioya.pagos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalle de una liquidación para el admin (HU-19 R36): sus líneas, la cuenta vigente del arrendador (o nula), si es
 * pagable y, si está PAGADA, a dónde y con qué operación se pagó.
 */
public record LiquidacionAdminDetalleDTO(Long id, Long arrendadorId, String arrendadorNombre, LocalDate periodo,
                                         Instant generadaEn, String estado, BigDecimal montoBruto,
                                         BigDecimal comision, BigDecimal montoNeto, boolean simulada,
                                         List<String> avisos, boolean pagable, CuentaDestinoDTO cuenta,
                                         PagoLiquidacionDTO pago, List<LineaLiquidacionDTO> reservas) {
}
