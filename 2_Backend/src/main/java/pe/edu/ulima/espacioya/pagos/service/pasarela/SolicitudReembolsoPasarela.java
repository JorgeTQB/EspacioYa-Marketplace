package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.math.BigDecimal;

/**
 * Reembolso que se pide a la pasarela (HU-16, ★ Adapter): de {@code monto} sobre la transacción original
 * {@code idTransaccion}. La {@code claveIdempotencia} es la misma en todos los intentos del mismo reembolso (D6).
 * {@code numeroIntento} es {@code reembolso.intentos} ya confirmado en la base para este intento (HU-31): solo lo usa
 * la simulada; Mercado Pago lo ignora.
 */
public record SolicitudReembolsoPasarela(String idTransaccion, BigDecimal monto, String claveIdempotencia,
                                         int numeroIntento) {

    public SolicitudReembolsoPasarela {
        if (numeroIntento < 1) {
            throw new IllegalArgumentException("El número de intento debe ser al menos 1");
        }
    }
}
