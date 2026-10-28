package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TokenTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("HU-04 R2, R3: token RECUPERACION con el hash, creado ahora, vence en 30 min y sin usar")
    void recuperacionFijaFechasYEstado() {
        var token = Token.recuperacion(7L, HASH, AHORA);

        assertThat(token.getUsuarioId()).isEqualTo(7L);
        assertThat(token.getTipo()).isEqualTo(TipoToken.RECUPERACION);
        assertThat(token.getHashToken()).isEqualTo(HASH);
        assertThat(token.getCreadoEn()).isEqualTo(AHORA);
        assertThat(token.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(Token.VIGENCIA_RECUPERACION).isEqualTo(Duration.ofMinutes(30));
        assertThat(token.getUsadoEn()).isNull();
        assertThat(token.fueUsado()).isFalse();
    }

    @Test
    @DisplayName("HU-04 R16: con expira_en igual o anterior a ahora el token expiró")
    void expiraEnElLimiteExacto() {
        var token = Token.recuperacion(7L, HASH, AHORA);
        var limite = AHORA.plus(Duration.ofMinutes(30));

        assertThat(token.expiro(limite.minusNanos(1))).isFalse();
        assertThat(token.expiro(limite)).isTrue();
        assertThat(token.expiro(limite.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("HU-04 R16: un token con usado_en fijado fue usado")
    void tokenUsado() {
        var token = Token.recuperacion(7L, HASH, AHORA);
        ReflectionTestUtils.setField(token, "usadoEn", AHORA.plusSeconds(60));

        assertThat(token.fueUsado()).isTrue();
    }
}
