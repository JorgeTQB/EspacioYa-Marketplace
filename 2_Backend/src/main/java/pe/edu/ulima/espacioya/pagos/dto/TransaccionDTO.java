package pe.edu.ulima.espacioya.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del historial de pagos (HU-28 R1, R4, R18). {@code id} es el del pago y el número del comprobante. Nunca lleva
 * los últimos 4 dígitos, el id de la pasarela ni el usuario. {@code montoReembolsado} es nulo si el pago está
 * {@code PAGADO}.
 */
public record TransaccionDTO(Long id, Long reservaId, LocalDate fecha, String espacio, BigDecimal monto,
                             String estado, BigDecimal montoReembolsado, boolean simulada) {
}
