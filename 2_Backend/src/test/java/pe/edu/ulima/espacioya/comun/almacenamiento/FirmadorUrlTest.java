package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FirmadorUrlTest {

    static final String LLAVE_FIRMA = "cHJ1ZWJhcy1lc3BhY2lveWEtZmlybWEtdXJsLTAwMDM=";
    static final String LLAVE_CIFRADO = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    static final String LLAVE_HUELLA = "ZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXp7fH1+f4CBgoM=";
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String CLAVE = "identidad/0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String TIPO = "application/pdf";

    private final FirmadorUrl firmador = firmadorEn(AHORA);

    private static FirmadorUrl firmadorEn(Instant instante) {
        return new FirmadorUrl(LLAVE_FIRMA, LLAVE_CIFRADO, LLAVE_HUELLA, Clock.fixed(instante, ZoneOffset.UTC));
    }

    private static Map<String, String> parametros(String url) {
        var resultado = new LinkedHashMap<String, String>();
        for (var par : URI.create(url).getRawQuery().split("&")) {
            var partes = par.split("=", 2);
            resultado.put(partes[0], URLDecoder.decode(partes[1], StandardCharsets.UTF_8));
        }
        return resultado;
    }

    @Test
    @DisplayName("HU-05 R38: la URL vence a los 5 minutos del reloj y lleva clave, tipo, expira y firma")
    void firmaConVencimientoDeCincoMinutos() {
        var firmada = firmador.firmar(CLAVE, TIPO);
        var parametros = parametros(firmada.url());

        assertThat(firmada.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(firmada.url()).startsWith("/api/archivos?");
        assertThat(parametros).containsOnlyKeys("clave", "tipo", "expira", "firma");
        assertThat(parametros.get("clave")).isEqualTo(CLAVE);
        assertThat(parametros.get("tipo")).isEqualTo(TIPO);
        assertThat(Long.parseLong(parametros.get("expira"))).isEqualTo(AHORA.plusSeconds(300).getEpochSecond());
        assertThat(firmador.esValida(CLAVE, TIPO, Long.parseLong(parametros.get("expira")), parametros.get("firma")))
                .isTrue();
    }

    @Test
    @DisplayName("HU-05 R36: cambiar la clave, el tipo, el vencimiento o la firma invalida la URL")
    void alteracionInvalida() {
        var parametros = parametros(firmador.firmar(CLAVE, TIPO).url());
        var expira = Long.parseLong(parametros.get("expira"));
        var firma = parametros.get("firma");
        var otraFirma = (firma.charAt(0) == 'A' ? "B" : "A") + firma.substring(1);

        assertThat(firmador.esValida("identidad/1f8fad5b-d9cb-469f-a165-70867728950e", TIPO, expira, firma))
                .isFalse();
        assertThat(firmador.esValida(CLAVE, "image/png", expira, firma)).isFalse();
        assertThat(firmador.esValida(CLAVE, TIPO, expira + 3600, firma)).isFalse();
        assertThat(firmador.esValida(CLAVE, TIPO, expira, otraFirma)).isFalse();
        assertThat(firmador.esValida(CLAVE, TIPO, expira, "")).isFalse();
        assertThat(firmador.esValida(CLAVE, TIPO, expira, null)).isFalse();
        assertThat(firmador.esValida(null, TIPO, expira, firma)).isFalse();
    }

    @Test
    @DisplayName("HU-05 R36: una URL firmada con otra llave no es válida")
    void otraLlaveInvalida() {
        var otro = new FirmadorUrl(Base64.getEncoder().encodeToString(new byte[32]), LLAVE_CIFRADO, LLAVE_HUELLA,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        var parametros = parametros(otro.firmar(CLAVE, TIPO).url());

        assertThat(firmador.esValida(CLAVE, TIPO, Long.parseLong(parametros.get("expira")), parametros.get("firma")))
                .isFalse();
    }

    @Test
    @DisplayName("HU-05 R37: una URL deja de ser válida cuando expira es igual o anterior al reloj")
    void vencidaInvalida() {
        var parametros = parametros(firmador.firmar(CLAVE, TIPO).url());
        var expira = Long.parseLong(parametros.get("expira"));
        var firma = parametros.get("firma");

        assertThat(firmadorEn(AHORA.plus(Duration.ofMinutes(5)).minusSeconds(1)).esValida(CLAVE, TIPO, expira, firma))
                .isTrue();
        assertThat(firmadorEn(AHORA.plus(Duration.ofMinutes(5))).esValida(CLAVE, TIPO, expira, firma)).isFalse();
        assertThat(firmadorEn(AHORA.plus(Duration.ofHours(1))).esValida(CLAVE, TIPO, expira, firma)).isFalse();
    }

    @Test
    @DisplayName("HU-05 R39: una llave de firma vacía, ausente o que no mide 32 bytes impide crearlo sin mostrarla")
    void llaveInvalidaFalla() {
        var dieciseisBytes = Base64.getEncoder().encodeToString(new byte[16]);
        for (var invalida : new String[] {"", "  ", null, "no-es-base64!!", dieciseisBytes}) {
            assertThatThrownBy(() -> new FirmadorUrl(invalida, LLAVE_CIFRADO, LLAVE_HUELLA, Clock.systemUTC()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ALMACENAMIENTO_LLAVE_FIRMA");
        }
        assertThatThrownBy(() -> new FirmadorUrl(dieciseisBytes, LLAVE_CIFRADO, LLAVE_HUELLA, Clock.systemUTC()))
                .hasMessageNotContaining(dieciseisBytes);
    }

    @Test
    @DisplayName("HU-05 R39: una llave de firma igual a CIFRADO_LLAVE o CIFRADO_LLAVE_HUELLA impide crearlo")
    void llaveIgualACifradoFalla() {
        assertThatThrownBy(() -> new FirmadorUrl(LLAVE_CIFRADO, LLAVE_CIFRADO, LLAVE_HUELLA, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALMACENAMIENTO_LLAVE_FIRMA")
                .hasMessageNotContaining(LLAVE_CIFRADO);
        assertThatThrownBy(() -> new FirmadorUrl(LLAVE_HUELLA, LLAVE_CIFRADO, LLAVE_HUELLA, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALMACENAMIENTO_LLAVE_FIRMA")
                .hasMessageNotContaining(LLAVE_HUELLA);
    }
}
