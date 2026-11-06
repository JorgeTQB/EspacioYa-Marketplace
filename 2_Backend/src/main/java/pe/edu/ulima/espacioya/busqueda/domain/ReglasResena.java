package pe.edu.ulima.espacioya.busqueda.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Regla pura de RN-13 (DT-4, DT-5): una reserva se reseña en el intervalo semiabierto
 * {@code fin ≤ ahora < fin + PLAZO}. Lima no tiene horario de verano, así que 14 × 24 h y 14 días de calendario
 * coinciden.
 */
public final class ReglasResena {

    public static final Duration PLAZO = Duration.ofDays(14);

    private ReglasResena() {
    }

    /** Vacío si está dentro del plazo. No confirmada o sin terminar: NO_USADA; ahora ≥ fin + PLAZO: PLAZO_VENCIDO. */
    public static Optional<MotivoNoResenable> motivoNoResenable(boolean confirmada, Instant fin, Instant ahora) {
        if (!confirmada || ahora.isBefore(fin)) {
            return Optional.of(MotivoNoResenable.NO_USADA);
        }
        if (!ahora.isBefore(fin.plus(PLAZO))) {
            return Optional.of(MotivoNoResenable.PLAZO_VENCIDO);
        }
        return Optional.empty();
    }
}
