package pe.edu.ulima.espacioya.busqueda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResenaTest {

    private static Resena visible() {
        return Resena.nueva(3L, 5L, 7L, 4, "Buena cancha", Instant.parse("2026-11-01T15:00:00Z"));
    }

    @Test
    @DisplayName("HU-27 R34: ocultar una reseña VISIBLE la deja OCULTA")
    void ocultaUnaVisible() {
        var resena = visible();
        assertThat(resena.estaVisible()).isTrue();

        resena.ocultar();

        assertThat(resena.getEstado()).isEqualTo(EstadoResena.OCULTA);
        assertThat(resena.estaVisible()).isFalse();
    }

    @Test
    @DisplayName("HU-27 R40: ocultar una reseña ya OCULTA lanza y no la cambia")
    void noOcultaDosVeces() {
        var resena = visible();
        resena.ocultar();

        assertThatThrownBy(resena::ocultar).isInstanceOf(IllegalStateException.class);
        assertThat(resena.getEstado()).isEqualTo(EstadoResena.OCULTA);
    }
}
