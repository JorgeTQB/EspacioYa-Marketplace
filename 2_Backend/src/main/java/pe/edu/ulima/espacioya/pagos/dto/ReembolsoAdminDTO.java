package pe.edu.ulima.espacioya.pagos.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Un reembolso por atender o el resultado de un reintento (HU-16 CA2). Sin datos del usuario ni de la tarjeta (D8). */
public record ReembolsoAdminDTO(Long id, Long reservaId, BigDecimal monto, String origen, String estado, int intentos,
                                Instant creadoEn, Instant ultimoIntentoEn) {
}
