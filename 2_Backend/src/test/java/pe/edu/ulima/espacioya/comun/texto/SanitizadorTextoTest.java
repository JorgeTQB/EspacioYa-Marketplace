package pe.edu.ulima.espacioya.comun.texto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SanitizadorTextoTest {

    @Test
    @DisplayName("HU-24 R21: quita las etiquetas de un script y deja su contenido como texto")
    void script() {
        assertThat(SanitizadorTexto.limpiar("Buena<script>alert('x')</script> cancha"))
                .isEqualTo("Buenaalert('x') cancha");
    }

    @Test
    @DisplayName("HU-24 R21: quita una etiqueta con atributos de evento")
    void atributosDeEvento() {
        assertThat(SanitizadorTexto.limpiar("Hola <img src=x onerror=\"alert(1)\"> mundo")).isEqualTo("Hola  mundo");
    }

    @Test
    @DisplayName("HU-24 R21: una etiqueta anidada no se recompone al quitar la de adentro")
    void etiquetaAnidada() {
        assertThat(SanitizadorTexto.limpiar("<scr<script>ipt>alert(1)</scr</script>ipt>"))
                .isEqualTo("scriptalert(1)/script");
    }

    @Test
    @DisplayName("HU-24 R21: un carácter de control no sirve para esconder una etiqueta")
    void controlDentroDeUnaEtiqueta() {
        assertThat(SanitizadorTexto.limpiar("a<scr\u0000ipt>b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("HU-24 R21: quita los < y > sueltos, sin rechazar el comentario")
    void angularesSueltos() {
        assertThat(SanitizadorTexto.limpiar("5 > 3")).isEqualTo("5  3");
        assertThat(SanitizadorTexto.limpiar("a < b")).isEqualTo("a  b");
        assertThat(SanitizadorTexto.limpiar("<<<>>>")).isEmpty();
    }

    @Test
    @DisplayName("HU-24 R21: normaliza los saltos de línea a \\n y conserva \\n y \\t")
    void saltosDeLinea() {
        assertThat(SanitizadorTexto.limpiar("uno\r\ndos\rtres\nc\tuatro")).isEqualTo("uno\ndos\ntres\nc\tuatro");
    }

    @Test
    @DisplayName("HU-24 R21: quita U+0000 y los demás caracteres de control")
    void caracteresDeControl() {
        assertThat(SanitizadorTexto.limpiar("a\u0000b\u0007c\u007Fd\u0085e")).isEqualTo("abcde");
    }

    @Test
    @DisplayName("HU-24 R21: las tildes, la ñ, el & y las comillas quedan intactos")
    void textoNormalIntacto() {
        var texto = "Muy buena atención & cancha; el baño está limpio, \"¡excelente!\" 'sí'";

        assertThat(SanitizadorTexto.limpiar(texto)).isEqualTo(texto);
    }

    @Test
    @DisplayName("HU-24 R21: recorta los espacios de los extremos")
    void recortaExtremos() {
        assertThat(SanitizadorTexto.limpiar("  \n hola \t ")).isEqualTo("hola");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<b></b>", "<script>", " <a href='x'> ", "\u0000", "< >", ">"})
    @DisplayName("HU-24 R22: un texto que solo tenía etiquetas o control queda vacío")
    void quedaVacio(String texto) {
        assertThat(SanitizadorTexto.limpiar(texto)).isEmpty();
    }
}
