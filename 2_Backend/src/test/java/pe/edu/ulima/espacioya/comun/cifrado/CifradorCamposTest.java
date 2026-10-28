package pe.edu.ulima.espacioya.comun.cifrado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CifradorCamposTest {

    static final String LLAVE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    static final String LLAVE_HUELLA = "ZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXp7fH1+f4CBgoM=";
    private static final String OTRA_LLAVE = "cHJ1ZWJhcy1lc3BhY2lveWEtbGxhdmUtYWVzLTAwMDE=";

    private final CifradorCampos cifrador = new CifradorCampos(LLAVE, LLAVE_HUELLA);

    @Test
    @DisplayName("HU-02 R34: cifra con prefijo v1:, sin el texto claro, y al descifrar devuelve el original")
    void idaYVuelta() {
        var cifrado = cifrador.cifrar("20100070970");

        assertThat(cifrado).startsWith("v1:").doesNotContain("20100070970");
        assertThat(cifrador.descifrar(cifrado)).isEqualTo("20100070970");
        assertThat(cifrador.descifrar(cifrador.cifrar("Av. Javier Prado 4600, Surco"))).isEqualTo(
                "Av. Javier Prado 4600, Surco");
    }

    @Test
    @DisplayName("HU-02 R34: null se guarda y se lee como null")
    void nullEsNull() {
        assertThat(cifrador.cifrar(null)).isNull();
        assertThat(cifrador.descifrar(null)).isNull();
    }

    @Test
    @DisplayName("HU-02 R35: cifrar dos veces el mismo valor produce textos distintos")
    void dosCifradosDistintos() {
        assertThat(cifrador.cifrar("12345678")).isNotEqualTo(cifrador.cifrar("12345678"));
    }

    @Test
    @DisplayName("HU-02 R36: un texto cifrado con un byte alterado lanza excepción al descifrar")
    void byteAlteradoLanzaExcepcion() {
        var cifrado = cifrador.cifrar("12345678");
        var bytes = Base64.getDecoder().decode(cifrado.substring(3));
        bytes[bytes.length - 1] ^= 1;
        var alterado = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cifrador.descifrar(alterado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("12345678");
    }

    @Test
    @DisplayName("HU-02 R36: un texto cifrado con otra llave lanza excepción al descifrar")
    void otraLlaveLanzaExcepcion() {
        var cifradoConOtra = new CifradorCampos(OTRA_LLAVE, LLAVE_HUELLA).cifrar("12345678");

        assertThatThrownBy(() -> cifrador.descifrar(cifradoConOtra)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-02 R36: un valor sin el formato v1: lanza excepción al descifrar")
    void formatoDesconocidoLanzaExcepcion() {
        assertThatThrownBy(() -> cifrador.descifrar("12345678")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar("v1:%%%")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar("v1:AAAA")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-02 R38: una llave vacía, ausente, no base64 o de 16 bytes falla nombrando CIFRADO_LLAVE")
    void llaveInvalidaFalla() {
        var dieciseisBytes = Base64.getEncoder().encodeToString(new byte[16]);
        for (var invalida : new String[] {"", "   ", null, "no-es-base64!!", dieciseisBytes}) {
            assertThatThrownBy(() -> new CifradorCampos(invalida, LLAVE_HUELLA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CIFRADO_LLAVE")
                    .hasMessageNotContaining("CIFRADO_LLAVE_HUELLA");
        }
        assertThatThrownBy(() -> new CifradorCampos(dieciseisBytes, LLAVE_HUELLA))
                .hasMessageNotContaining(dieciseisBytes);
    }

    @Test
    @DisplayName("HU-02 R38: una llave de huella inválida también impide crear el cifrador")
    void llaveHuellaInvalidaFalla() {
        assertThatThrownBy(() -> new CifradorCampos(LLAVE, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CIFRADO_LLAVE_HUELLA");
    }

    @Test
    @DisplayName("HU-02 R39: si la llave de cifrado y la de huella son iguales falla al crearse")
    void llavesIgualesFalla() {
        assertThatThrownBy(() -> new CifradorCampos(LLAVE, LLAVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(LLAVE);
    }
}
