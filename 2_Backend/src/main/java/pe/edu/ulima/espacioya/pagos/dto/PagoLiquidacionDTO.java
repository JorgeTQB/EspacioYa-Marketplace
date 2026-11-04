package pe.edu.ulima.espacioya.pagos.dto;

import java.time.Instant;

/** Cómo se pagó una liquidación PAGADA (HU-19 CA2, S7): operación, fecha y destino enmascarado. */
public record PagoLiquidacionDTO(String numeroOperacion, Instant pagadaEn, String banco, String cuentaEnmascarada) {
}
