package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DesafioMfaTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final String HASH = "c".repeat(64);

    @Test
    @DisplayName("HU-17 R5: crear fija usuario, paso, creado_en, expira_en a +5 min, 0 fallos y abierto")
    void crearFijaCampos() {
        var desafio = DesafioMfa.crear(7L, HASH, PasoMfa.CODIGO, AHORA);

        assertThat(desafio.getUsuarioId()).isEqualTo(7L);
        assertThat(desafio.getHashToken()).isEqualTo(HASH);
        assertThat(desafio.getPaso()).isEqualTo(PasoMfa.CODIGO);
        assertThat(desafio.getCreadoEn()).isEqualTo(AHORA);
        assertThat(desafio.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(desafio.getIntentosFallidos()).isZero();
        assertThat(desafio.getCerradoEn()).isNull();
        assertThat(desafio.estaAbierto(AHORA)).isTrue();
    }

    @Test
    @DisplayName("HU-17 R18: con expira_en igual a ahora el desafío ya no está abierto")
    void noAbiertoEnElLimiteExacto() {
        var desafio = DesafioMfa.crear(7L, HASH, PasoMfa.CONFIGURACION, AHORA);
        var expira = AHORA.plus(Duration.ofMinutes(5));

        assertThat(desafio.estaAbierto(expira.minusNanos(1))).isTrue();
        assertThat(desafio.estaAbierto(expira)).isFalse();
    }

    @Test
    @DisplayName("HU-17 R20, R21: 4 fallos lo dejan abierto; el 5.º lo cierra y devuelve true")
    void quintoFalloCierra() {
        var desafio = DesafioMfa.crear(7L, HASH, PasoMfa.CODIGO, AHORA);
        var cuando = AHORA.plusSeconds(30);

        for (int i = 1; i <= 4; i++) {
            assertThat(desafio.registrarFallo(cuando)).isFalse();
            assertThat(desafio.getIntentosFallidos()).isEqualTo(i);
            assertThat(desafio.estaAbierto(cuando)).isTrue();
        }
        assertThat(desafio.registrarFallo(cuando)).isTrue();

        assertThat(desafio.getIntentosFallidos()).isEqualTo(5);
        assertThat(desafio.getCerradoEn()).isEqualTo(cuando);
        assertThat(desafio.estaAbierto(cuando)).isFalse();
    }

    @Test
    @DisplayName("HU-17 R13: cerrar fija cerrado_en y un segundo cierre falla")
    void cerrarDosVecesFalla() {
        var desafio = DesafioMfa.crear(7L, HASH, PasoMfa.CODIGO, AHORA);

        desafio.cerrar(AHORA);

        assertThat(desafio.getCerradoEn()).isEqualTo(AHORA);
        assertThat(desafio.estaAbierto(AHORA)).isFalse();
        assertThatThrownBy(() -> desafio.cerrar(AHORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> desafio.registrarFallo(AHORA)).isInstanceOf(IllegalStateException.class);
    }
}
