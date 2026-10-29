package pe.edu.ulima.espacioya.comun.configuracion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Fecha y hora locales de Lima a partir del {@link Clock} del sistema, que sigue en UTC (HU-08 DT-1). "Hoy" en UTC
 * cambia de día a las 19:00 de Lima: las reglas de calendario (bloqueos, ventana de reserva) usan siempre esta zona.
 * Se lee {@code reloj.instant()} y no {@code reloj.withZone(...)} para que funcione con el reloj controlable de las IT.
 */
public final class ZonaHoraria {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private ZonaHoraria() {
    }

    public static LocalDate hoy(Clock reloj) {
        return LocalDate.ofInstant(reloj.instant(), LIMA);
    }

    public static LocalDateTime ahora(Clock reloj) {
        return LocalDateTime.ofInstant(reloj.instant(), LIMA);
    }
}
