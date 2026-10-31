package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservaPorCobrar(Long id, BigDecimal montoTotal, Instant expiraEn, boolean vigente,
                               boolean espacioVisible) {
}
