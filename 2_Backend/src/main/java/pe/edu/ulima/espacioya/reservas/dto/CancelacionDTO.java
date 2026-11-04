package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Resultado de la cancelación (HU-15 CA3). */
public record CancelacionDTO(Long reservaId, String estado, String canceladaPor, Instant canceladaEn, String politica,
                             int porcentaje, BigDecimal montoReembolso) {
}
