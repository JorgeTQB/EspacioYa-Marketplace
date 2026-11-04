package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.math.BigDecimal;

/**
 * Reembolso que se pide a la pasarela (HU-16, ★ Adapter): de {@code monto} sobre la transacción original
 * {@code idTransaccion}. La {@code claveIdempotencia} es la misma en todos los intentos del mismo reembolso (D6).
 */
public record SolicitudReembolsoPasarela(String idTransaccion, BigDecimal monto, String claveIdempotencia) {
}
