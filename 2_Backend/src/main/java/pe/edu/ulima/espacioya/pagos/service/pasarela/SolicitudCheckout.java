package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.math.BigDecimal;
import java.time.Instant;

public record SolicitudCheckout(Long reservaId, BigDecimal monto, Instant venceEn, String urlRetorno) {
}
