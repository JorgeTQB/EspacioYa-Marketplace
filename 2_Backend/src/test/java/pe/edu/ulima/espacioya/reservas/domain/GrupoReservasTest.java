package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrupoReservasTest {

    @Test
    @DisplayName("HU-14 R30: proximas y pasadas exactos son grupos válidos")
    void gruposValidos() {
        assertThat(GrupoReservas.desde("proximas")).contains(GrupoReservas.PROXIMAS);
        assertThat(GrupoReservas.desde("pasadas")).contains(GrupoReservas.PASADAS);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PROXIMAS", "Pasadas", " proximas", "proximas ", "todas", "", "próximas", "PROXIMAS "})
    @DisplayName("HU-14 R30: cualquier otro texto, en blanco o null no es un grupo")
    void gruposInvalidos(String texto) {
        assertThat(GrupoReservas.desde(texto)).isEmpty();
    }
}
