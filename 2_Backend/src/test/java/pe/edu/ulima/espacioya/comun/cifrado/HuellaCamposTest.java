package pe.edu.ulima.espacioya.comun.cifrado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HuellaCamposTest {

    private static final String OTRA_LLAVE = "cHJ1ZWJhcy1lc3BhY2lveWEtbGxhdmUtaG1hYy0wMDI=";

    private final HuellaCampos huella = new HuellaCampos(CifradorCamposTest.LLAVE_HUELLA);

    @Test
    @DisplayName("HU-02 R37: la huella es determinista y tiene 64 caracteres hexadecimales")
    void deterministaYHexadecimal() {
        var primera = huella.calcular("20100070970");

        assertThat(primera).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(huella.calcular("20100070970")).isEqualTo(primera);
        assertThat(huella.calcular("20131312955")).isNotEqualTo(primera);
    }

    @Test
    @DisplayName("HU-02 R37: con otra llave la huella cambia")
    void otraLlaveOtraHuella() {
        assertThat(new HuellaCampos(OTRA_LLAVE).calcular("12345678")).isNotEqualTo(huella.calcular("12345678"));
    }

    @Test
    @DisplayName("HU-02 R37: la huella no es el SHA-256 sin llave del número")
    void distintaDelSha256() throws Exception {
        var sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("12345678".getBytes(StandardCharsets.UTF_8)));

        assertThat(huella.calcular("12345678")).isNotEqualTo(sha);
    }

    @Test
    @DisplayName("HU-02 R38: una llave de huella vacía, no base64 o de 16 bytes falla nombrando CIFRADO_LLAVE_HUELLA")
    void llaveInvalidaFalla() {
        var dieciseisBytes = Base64.getEncoder().encodeToString(new byte[16]);
        for (var invalida : new String[] {"", null, "***", dieciseisBytes}) {
            assertThatThrownBy(() -> new HuellaCampos(invalida))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CIFRADO_LLAVE_HUELLA");
        }
        assertThatThrownBy(() -> new HuellaCampos(dieciseisBytes)).hasMessageNotContaining(dieciseisBytes);
    }
}
