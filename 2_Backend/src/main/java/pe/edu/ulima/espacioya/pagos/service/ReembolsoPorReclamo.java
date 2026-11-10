package pe.edu.ulima.espacioya.pagos.service;

import java.math.BigDecimal;

/**
 * Solicitud de reembolso por un reclamo resuelto a favor del usuario (HU-26 R44). {@code solicitadoPor} es el admin
 * que resolvió e {@code ip} la suya.
 */
public record ReembolsoPorReclamo(Long reservaId, BigDecimal monto, Long solicitadoPor, String ip) {
}
