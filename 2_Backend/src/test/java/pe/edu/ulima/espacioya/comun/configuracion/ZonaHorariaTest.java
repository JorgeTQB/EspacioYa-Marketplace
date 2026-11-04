package pe.edu.ulima.espacioya.comun.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZonaHorariaTest {

    private static Clock reloj(String instante) {
        return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("HU-08 R29: a las 03:00 UTC todavía es el día anterior en Lima, a las 22:00")
    void calculaHoyYAhoraEnLima() {
        var reloj = reloj("2026-10-29T03:00:00Z");

        assertThat(ZonaHoraria.hoy(reloj)).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(ZonaHoraria.ahora(reloj)).isEqualTo(LocalDateTime.of(2026, 10, 28, 22, 0));
    }

    @Test
    @DisplayName("HU-08 R29: a las 05:00 UTC empieza el día siguiente en Lima")
    void cambiaDeDiaALaMedianocheDeLima() {
        var reloj = reloj("2026-10-29T05:00:00Z");

        assertThat(ZonaHoraria.hoy(reloj)).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(ZonaHoraria.ahora(reloj)).isEqualTo(LocalDateTime.of(2026, 10, 29, 0, 0));
    }
}
