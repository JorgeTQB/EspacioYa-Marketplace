package pe.edu.ulima.espacioya.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenAleatorioTest {

    @Test
    @DisplayName("HU-03 R6: el token tiene 43 caracteres base64url sin relleno")
    void tokenEnBase64UrlSinRelleno() {
        var token = TokenAleatorio.generar();

        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    @DisplayName("HU-03 R6: dos tokens generados son distintos")
    void dosTokensDistintos() {
        assertThat(TokenAleatorio.generar()).isNotEqualTo(TokenAleatorio.generar());
    }

    @Test
    @DisplayName("HU-03 R3: el hash es el SHA-256 en hexadecimal minúsculo de 64 caracteres")
    void hashEsSha256Hex() {
        assertThat(TokenAleatorio.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .hasSize(64);
    }

    @Test
    @DisplayName("HU-03 R3: el hash del token no contiene el token en claro")
    void hashDistintoDelToken() {
        var token = TokenAleatorio.generar();

        assertThat(TokenAleatorio.hash(token)).isNotEqualTo(token).doesNotContain(token);
    }

    @Test
    @DisplayName("HU-04 R17: un token generado tiene forma válida")
    void tokenGeneradoTieneFormato() {
        assertThat(TokenAleatorio.tieneFormato(TokenAleatorio.generar())).isTrue();
        assertThat(TokenAleatorio.tieneFormato("A".repeat(21) + "-_" + "z9".repeat(10))).isTrue();
    }

    @Test
    @DisplayName("HU-04 R17: nulo, vacío, 42 o 44 caracteres y los caracteres +, / y = no tienen forma válida")
    void formasInvalidas() {
        var valido = TokenAleatorio.generar();

        assertThat(TokenAleatorio.tieneFormato(null)).isFalse();
        assertThat(TokenAleatorio.tieneFormato("")).isFalse();
        assertThat(TokenAleatorio.tieneFormato(valido.substring(1))).isFalse();
        assertThat(TokenAleatorio.tieneFormato(valido + "A")).isFalse();
        assertThat(TokenAleatorio.tieneFormato("+" + valido.substring(1))).isFalse();
        assertThat(TokenAleatorio.tieneFormato("/" + valido.substring(1))).isFalse();
        assertThat(TokenAleatorio.tieneFormato(valido.substring(1) + "=")).isFalse();
        assertThat(TokenAleatorio.tieneFormato(" " + valido.substring(1))).isFalse();
    }
}
