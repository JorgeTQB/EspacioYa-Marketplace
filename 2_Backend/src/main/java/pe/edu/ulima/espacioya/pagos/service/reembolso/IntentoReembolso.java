package pe.edu.ulima.espacioya.pagos.service.reembolso;

import java.math.BigDecimal;

/**
 * Lo que el paso 1 del comando reclamó, leído bajo bloqueo de la fila de {@code reembolso} y de su pago original: la
 * transacción y el monto salen solo de la base (HU-16 R9). {@code numero} es el valor de {@code intentos} tras sumar 1.
 */
public record IntentoReembolso(Long reembolsoId, Long pagoId, Long reservaId, String idTransaccion, BigDecimal monto,
                               int numero, String claveIdempotencia) {
}
