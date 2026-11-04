package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodigoRespaldoTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final String HASH = "b".repeat(64);

    @Test
    @DisplayName("HU-17 R37: nuevo guarda la huella del código, sin usar")
    void nuevoSinUsar() {
        var codigo = CodigoRespaldo.nuevo(7L, HASH, AHORA);

        assertThat(codigo.getUsuarioId()).isEqualTo(7L);
        assertThat(codigo.getHashCodigo()).isEqualTo(HASH);
        assertThat(codigo.getCreadoEn()).isEqualTo(AHORA);
        assertThat(codigo.getUsadoEn()).isNull();
        assertThat(codigo.estaUsado()).isFalse();
    }

    @Test
    @DisplayName("HU-17 R16: usar fija usado_en y un segundo uso falla")
    void usarDosVecesFalla() {
        var codigo = CodigoRespaldo.nuevo(7L, HASH, AHORA);
        var despues = AHORA.plus(Duration.ofMinutes(3));

        codigo.usar(despues);

        assertThat(codigo.estaUsado()).isTrue();
        assertThat(codigo.getUsadoEn()).isEqualTo(despues);
        assertThatThrownBy(() -> codigo.usar(despues.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThat(codigo.getUsadoEn()).isEqualTo(despues);
    }
}
