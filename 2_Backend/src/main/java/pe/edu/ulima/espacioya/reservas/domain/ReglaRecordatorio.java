package pe.edu.ulima.espacioya.reservas.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;

/**
 * Plazo del recordatorio de reserva (HU-25 D2, RN-14): {@code inicio − 24 h ≤ ahora < inicio}. Una reserva confirmada
 * con menos de 24 h de anticipación entra igual en la siguiente pasada; una que ya empezó, nunca.
 */
public final class ReglaRecordatorio {

    public static final Duration ANTICIPACION = Duration.ofHours(24);

    private ReglaRecordatorio() {
    }

    public static boolean corresponde(Instant inicio, Instant ahora) {
        return !ahora.isBefore(inicio.minus(ANTICIPACION)) && ahora.isBefore(inicio);
    }

    /** Fecha de Lima de {@code ahora + 24 h}: el último día en que puede empezar una reserva que ya entra al plazo. */
    public static LocalDate ultimaFecha(Instant ahora) {
        return LocalDate.ofInstant(ahora.plus(ANTICIPACION), ZonaHoraria.LIMA);
    }
}
