package pe.edu.ulima.espacioya.pagos.service;

import java.math.BigDecimal;

/** Solicitud de reembolso de una cancelación (HU-15 D7). {@code solicitadoPor} e {@code ip} son de la sesión. */
public record ReembolsoPorCancelacion(Long reservaId, BigDecimal monto, Long solicitadoPor, String ip) {
}
