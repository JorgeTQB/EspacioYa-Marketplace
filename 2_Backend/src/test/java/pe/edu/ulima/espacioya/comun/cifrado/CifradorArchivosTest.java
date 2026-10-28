package pe.edu.ulima.espacioya.comun.cifrado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CifradorArchivosTest {

    private static final String LLAVE = CifradorCamposTest.LLAVE;
    private static final String OTRA_LLAVE = CifradorCamposTest.LLAVE_HUELLA;
    private static final byte[] CONTENIDO =
            "%PDF-1.4 documento de identidad de prueba".getBytes(StandardCharsets.UTF_8);

    private final CifradorArchivos cifrador = new CifradorArchivos(LLAVE);

    @Test
    @DisplayName("HU-05 R30: cifra con prefijo EYA1, sin el contenido claro, y al descifrar devuelve el original")
    void idaYVuelta() {
        var cifrado = cifrador.cifrar(CONTENIDO);

        assertThat(Arrays.copyOf(cifrado, 4)).isEqualTo("EYA1".getBytes(StandardCharsets.US_ASCII));
        assertThat(cifrado).hasSize(4 + 12 + CONTENIDO.length + 16);
        assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
        assertThat(cifrador.descifrar(cifrado)).isEqualTo(CONTENIDO);
    }

    @Test
    @DisplayName("HU-05 R30: cifrar dos veces el mismo contenido produce bytes distintos")
    void dosCifradosDistintos() {
        assertThat(cifrador.cifrar(CONTENIDO)).isNotEqualTo(cifrador.cifrar(CONTENIDO));
    }

    @Test
    @DisplayName("HU-05 R30: un archivo con un byte alterado lanza excepción sin datos al descifrar")
    void byteAlteradoLanzaExcepcion() {
        var cifrado = cifrador.cifrar(CONTENIDO);
        cifrado[cifrado.length - 1] ^= 1;

        assertThatThrownBy(() -> cifrador.descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("PDF")
                .hasMessageNotContaining(LLAVE);
    }

    @Test
    @DisplayName("HU-05 R30: solo se recupera con la llave de cifrado: con otra llave falla")
    void otraLlaveFalla() {
        var cifradoConOtra = new CifradorArchivos(OTRA_LLAVE).cifrar(CONTENIDO);

        assertThatThrownBy(() -> cifrador.descifrar(cifradoConOtra)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-05 R30: un contenido sin el prefijo EYA1 o incompleto lanza excepción al descifrar")
    void formatoDesconocidoFalla() {
        assertThatThrownBy(() -> cifrador.descifrar(CONTENIDO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar("EYA1".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-05 R30: una llave inválida falla nombrando CIFRADO_LLAVE sin mostrar su valor")
    void llaveInvalidaFalla() {
        var dieciseisBytes = Base64.getEncoder().encodeToString(new byte[16]);
        for (var invalida : new String[] {"", null, "no-es-base64!!", dieciseisBytes}) {
            assertThatThrownBy(() -> new CifradorArchivos(invalida))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CIFRADO_LLAVE");
        }
        assertThatThrownBy(() -> new CifradorArchivos(dieciseisBytes)).hasMessageNotContaining(dieciseisBytes);
    }
}
