package pe.edu.ulima.espacioya.pagos.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Proyección del listado del admin (HU-16 R28): el reembolso con la reserva de su pago, en una consulta. */
public record ReembolsoPorAtender(Long id, Long reservaId, BigDecimal monto, OrigenReembolso origen,
                                  EstadoReembolso estado, int intentos, Instant creadoEn, Instant ultimoIntentoEn) {
}
