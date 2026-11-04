package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MfaTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String OTRO_SECRETO = "MZXW6YTBOIMZXW6YTBOIMZXW6YTBOIAA";

    @Test
    @DisplayName("HU-17 R24: pendiente crea una configuración inactiva con el secreto y sin paso usado")
    void pendienteInactivo() {
        var mfa = Mfa.pendiente(7L, SECRETO, AHORA);

        assertThat(mfa.getUsuarioId()).isEqualTo(7L);
        assertThat(mfa.getSecreto()).isEqualTo(SECRETO);
        assertThat(mfa.estaActivo()).isFalse();
        assertThat(mfa.getCreadoEn()).isEqualTo(AHORA);
        assertThat(mfa.getActivadoEn()).isNull();
        assertThat(mfa.getUltimoPaso()).isNull();
        assertThat(mfa.isNew()).isTrue();
    }

    @Test
    @DisplayName("HU-17 R24: reemplazarSecreto cambia el secreto pendiente y limpia ultimo_paso")
    void reemplazarSecretoLimpiaUltimoPaso() {
        var mfa = Mfa.pendiente(7L, SECRETO, AHORA);
        mfa.registrarPaso(100L);
        var despues = AHORA.plus(Duration.ofMinutes(1));

        mfa.reemplazarSecreto(OTRO_SECRETO, despues);

        assertThat(mfa.getSecreto()).isEqualTo(OTRO_SECRETO);
        assertThat(mfa.getUltimoPaso()).isNull();
        assertThat(mfa.getCreadoEn()).isEqualTo(despues);
        assertThat(mfa.estaActivo()).isFalse();
    }

    @Test
    @DisplayName("HU-17 R30: reemplazarSecreto falla si el MFA ya está activo")
    void reemplazarSecretoFallaSiActivo() {
        var mfa = Mfa.pendiente(7L, SECRETO, AHORA);
        mfa.activar(AHORA);

        assertThatThrownBy(() -> mfa.reemplazarSecreto(OTRO_SECRETO, AHORA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mfa.getSecreto()).isEqualTo(SECRETO);
    }

    @Test
    @DisplayName("HU-17 R25, R31: activar deja activo = true con activado_en igual al reloj y no se repite")
    void activarFijaActivadoEn() {
        var mfa = Mfa.pendiente(7L, SECRETO, AHORA);
        var despues = AHORA.plus(Duration.ofMinutes(2));

        mfa.activar(despues);

        assertThat(mfa.estaActivo()).isTrue();
        assertThat(mfa.getActivadoEn()).isEqualTo(despues);
        assertThatThrownBy(() -> mfa.activar(despues)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-17 R14, R15: registrarPaso guarda el paso y rechaza uno igual o anterior al último")
    void registrarPasoRechazaRepetido() {
        var mfa = Mfa.pendiente(7L, SECRETO, AHORA);

        mfa.registrarPaso(100L);

        assertThat(mfa.getUltimoPaso()).isEqualTo(100L);
        assertThatThrownBy(() -> mfa.registrarPaso(100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mfa.registrarPaso(99L)).isInstanceOf(IllegalArgumentException.class);
        mfa.registrarPaso(101L);
        assertThat(mfa.getUltimoPaso()).isEqualTo(101L);
    }

    @Test
    @DisplayName("HU-17 R50: secretoEnBytes decodifica el Base32 guardado")
    void secretoEnBytes() {
        var mfa = Mfa.pendiente(7L, SECRETO, AHORA);

        assertThat(mfa.secretoEnBytes()).isEqualTo("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
    }
}
