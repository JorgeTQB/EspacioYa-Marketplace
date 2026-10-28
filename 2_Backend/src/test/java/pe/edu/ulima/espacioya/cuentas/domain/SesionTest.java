package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SesionTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("HU-03 R2: iniciar crea la sesión no revocada que expira a los 30 minutos")
    void iniciarFijaFechasYEstado() {
        var sesion = Sesion.iniciar(7L, HASH, AHORA);

        assertThat(sesion.getUsuarioId()).isEqualTo(7L);
        assertThat(sesion.getHashToken()).isEqualTo(HASH);
        assertThat(sesion.getCreadaEn()).isEqualTo(AHORA);
        assertThat(sesion.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(sesion.isRevocada()).isFalse();
        assertThat(sesion.estaVigente(AHORA)).isTrue();
    }

    @Test
    @DisplayName("HU-03 R24: renovar mueve expira_en a ahora más 30 minutos")
    void renovarMueveExpiracion() {
        var sesion = Sesion.iniciar(7L, HASH, AHORA);
        var despues = AHORA.plus(Duration.ofMinutes(20));

        sesion.renovar(despues);

        assertThat(sesion.getExpiraEn()).isEqualTo(despues.plus(Duration.ofMinutes(30)));
        assertThat(sesion.estaVigente(AHORA.plus(Duration.ofMinutes(45)))).isTrue();
    }

    @Test
    @DisplayName("HU-03 R27: con expira_en igual a ahora la sesión ya no está vigente")
    void noVigenteEnElLimiteExacto() {
        var sesion = Sesion.iniciar(7L, HASH, AHORA);

        assertThat(sesion.estaVigente(AHORA.plus(Duration.ofMinutes(30)).minusNanos(1))).isTrue();
        assertThat(sesion.estaVigente(AHORA.plus(Duration.ofMinutes(30)))).isFalse();
        assertThat(sesion.estaVigente(AHORA.plus(Duration.ofMinutes(31)))).isFalse();
    }

    @Test
    @DisplayName("HU-03 R25, R26: una sesión revocada no está vigente aunque no haya expirado")
    void revocadaNoVigente() {
        var sesion = Sesion.iniciar(7L, HASH, AHORA);

        sesion.revocar();

        assertThat(sesion.isRevocada()).isTrue();
        assertThat(sesion.estaVigente(AHORA)).isFalse();
    }
}
