package pe.edu.ulima.espacioya.espacios.service.puertos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BloqueReservadoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);
    private static final LocalDateTime AHORA = HOY.atTime(15, 0);

    @Test
    @DisplayName("HU-21 R15: un bloque que termina justo ahora ya no cuenta como por terminar")
    void bloqueQueTerminaAhoraNoCuenta() {
        assertThat(new BloqueReservado(HOY, 14, true).terminaDespuesDe(AHORA)).isFalse();
        assertThat(new BloqueReservado(HOY, 9, true).terminaDespuesDe(AHORA)).isFalse();
        assertThat(new BloqueReservado(HOY.minusDays(1), 23, true).terminaDespuesDe(AHORA)).isFalse();
    }

    @Test
    @DisplayName("HU-21 R14: un bloque en curso cuenta como por terminar")
    void bloqueEnCursoCuenta() {
        assertThat(new BloqueReservado(HOY, 15, true).terminaDespuesDe(AHORA)).isTrue();
        assertThat(new BloqueReservado(HOY, 14, false).terminaDespuesDe(AHORA.minusMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("HU-21 R13: un bloque futuro cuenta como por terminar")
    void bloqueFuturoCuenta() {
        assertThat(new BloqueReservado(HOY, 16, true).terminaDespuesDe(AHORA)).isTrue();
        assertThat(new BloqueReservado(HOY.plusDays(10), 8, true).terminaDespuesDe(AHORA)).isTrue();
    }

    @Test
    @DisplayName("HU-21 R13, R15: el bloque de 23:00 termina a medianoche del día siguiente")
    void bloqueDeLasOnceTerminaAMedianoche() {
        var bloque = new BloqueReservado(HOY, 23, true);

        assertThat(bloque.terminaDespuesDe(HOY.atTime(23, 59))).isTrue();
        assertThat(bloque.terminaDespuesDe(HOY.plusDays(1).atStartOfDay())).isFalse();
    }
}
