package pe.edu.ulima.espacioya.espacios.domain;

import java.time.LocalDate;

/** Las próximas 8 semanas: de hoy inclusive a hoy + 55 días inclusive (HU-08 DT-2). */
public record VentanaReserva(LocalDate desde, LocalDate hasta) {

    public static final int DIAS = 56;

    public static VentanaReserva desde(LocalDate hoy) {
        return new VentanaReserva(hoy, hoy.plusDays(DIAS - 1L));
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }
}
