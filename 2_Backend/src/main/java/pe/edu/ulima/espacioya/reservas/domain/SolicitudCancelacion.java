package pe.edu.ulima.espacioya.reservas.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Lo que una política de reembolso necesita saber, armado solo con datos del servidor (HU-15 R30). */
public record SolicitudCancelacion(QuienCancela quien, Instant inicio, Instant ahora, BigDecimal montoPagado) {

    public SolicitudCancelacion {
        Objects.requireNonNull(quien, "quien");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(ahora, "ahora");
        Objects.requireNonNull(montoPagado, "montoPagado");
    }

    /** Inicio de la reserva − ahora (R14). */
    public Duration anticipacion() {
        return Duration.between(ahora, inicio);
    }
}
