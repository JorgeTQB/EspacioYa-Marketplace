package pe.edu.ulima.espacioya.pagos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Un pago de una reserva para la exportación de HU-22 (R6). Sin {@code ultimos4} ni id de la pasarela (R13): no
 * identifican al titular y no son suyos para llevarse.
 */
public record PagoDeReserva(Long reservaId, BigDecimal monto, String estado, Instant creadoEn,
                            List<ReembolsoDePago> reembolsos) {
}
