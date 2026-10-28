package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PoliticaContrasenaTest {

    private static final String CORREO = "ana@correo.pe";

    @Test
    @DisplayName("HU-01 R10: acepta 8 y 64 caracteres y rechaza 7 y 65")
    void limitesDeLongitud() {
        assertThat(PoliticaContrasena.cumple("abc1234", CORREO)).isFalse();
        assertThat(PoliticaContrasena.cumple("abcd1234", CORREO)).isTrue();
        assertThat(PoliticaContrasena.cumple("a1" + "x".repeat(62), CORREO)).isTrue();
        assertThat(PoliticaContrasena.cumple("a1" + "x".repeat(63), CORREO)).isFalse();
    }

    @Test
    @DisplayName("HU-01 R10: rechaza una contraseña de 64 caracteres o menos que supera 72 bytes en UTF-8")
    void rechazaMasDe72Bytes() {
        var contrasena = "1" + "ñ".repeat(36);

        assertThat(contrasena.codePointCount(0, contrasena.length())).isLessThanOrEqualTo(64);
        assertThat(contrasena.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);
        assertThat(PoliticaContrasena.cumple(contrasena, CORREO)).isFalse();
        assertThat(PoliticaContrasena.cumple("1" + "ñ".repeat(35), CORREO)).isTrue();
    }

    @Test
    @DisplayName("HU-01 R10: una contraseña nula no cumple")
    void rechazaNula() {
        assertThat(PoliticaContrasena.cumple(null, CORREO)).isFalse();
    }

    @Test
    @DisplayName("HU-01 R11: rechaza la contraseña sin letra")
    void rechazaSinLetra() {
        assertThat(PoliticaContrasena.cumple("12345678", CORREO)).isFalse();
    }

    @Test
    @DisplayName("HU-01 R11: rechaza la contraseña sin dígito")
    void rechazaSinDigito() {
        assertThat(PoliticaContrasena.cumple("abcdefgh", CORREO)).isFalse();
    }

    @Test
    @DisplayName("HU-01 R12: rechaza la contraseña igual al correo aunque cambien las mayúsculas")
    void rechazaIgualAlCorreo() {
        assertThat(PoliticaContrasena.cumple("ANA1@correo.pe", "ana1@correo.pe")).isFalse();
        assertThat(PoliticaContrasena.cumple("ana1@correo.pe", "ana1@correo.pe")).isFalse();
    }

    @Test
    @DisplayName("HU-01 R10, R11, R12: acepta una contraseña que cumple la política")
    void aceptaContrasenaValida() {
        assertThat(PoliticaContrasena.cumple("Segura123!", CORREO)).isTrue();
    }
}
