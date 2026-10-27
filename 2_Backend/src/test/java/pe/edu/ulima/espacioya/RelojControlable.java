package pe.edu.ulima.espacioya;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj de prueba que solo avanza cuando el test lo pide.
 */
public class RelojControlable extends Clock {

    private final AtomicReference<Instant> ahora;

    public RelojControlable(Instant inicio) {
        this.ahora = new AtomicReference<>(inicio);
    }

    public void avanzar(Duration duracion) {
        ahora.updateAndGet(instante -> instante.plus(duracion));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora.get();
    }
}
