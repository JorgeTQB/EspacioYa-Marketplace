package pe.edu.ulima.espacioya.pagos.service;

import java.math.BigDecimal;
import java.time.Instant;

/** Un reembolso de un {@link PagoDeReserva} (HU-22 R6), sin el id del reembolso en la pasarela. */
public record ReembolsoDePago(BigDecimal monto, String estado, Instant creadoEn) {
}
