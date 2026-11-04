package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VentanaReservaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);

    @Test
    @DisplayName("HU-08 R13: la ventana va de hoy a hoy + 55 días (8 semanas)")
    void vaDeHoyAHoyMas55() {
        var ventana = VentanaReserva.desde(HOY);

        assertThat(ventana.desde()).isEqualTo(HOY);
        assertThat(ventana.hasta()).isEqualTo(HOY.plusDays(55));
    }

    @Test
    @DisplayName("HU-08 R13, R27: contiene sus bordes y nada fuera de ellos")
    void contieneSoloSusFechas() {
        var ventana = VentanaReserva.desde(HOY);

        assertThat(ventana.contiene(HOY.minusDays(1))).isFalse();
        assertThat(ventana.contiene(HOY)).isTrue();
        assertThat(ventana.contiene(HOY.plusDays(55))).isTrue();
        assertThat(ventana.contiene(HOY.plusDays(56))).isFalse();
    }
}
