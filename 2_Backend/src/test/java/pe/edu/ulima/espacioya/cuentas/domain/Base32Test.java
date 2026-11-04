package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Base32Test {

    @ParameterizedTest
    @CsvSource(value = {"'',''", "f,MY", "fo,MZXQ", "foo,MZXW6", "foob,MZXW6YQ", "fooba,MZXW6YTB",
        "foobar,MZXW6YTBOI"})
    @DisplayName("HU-17 R51: codifica los vectores de RFC 4648 § 10 sin relleno")
    void codificaVectoresRfc4648(String claro, String esperado) {
        assertThat(Base32.codificar(claro.getBytes(StandardCharsets.US_ASCII))).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(value = {"'',''", "f,MY", "fo,MZXQ", "foo,MZXW6", "foob,MZXW6YQ", "fooba,MZXW6YTB",
        "foobar,MZXW6YTBOI"})
    @DisplayName("HU-17 R51: decodifica los vectores de RFC 4648 § 10")
    void decodificaVectoresRfc4648(String esperado, String codificado) {
        assertThat(new String(Base32.decodificar(codificado), StandardCharsets.US_ASCII)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("HU-17 R51: el secreto ASCII del Apéndice B de RFC 6238 es GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
    void codificaSecretoDeRfc6238() {
        var secreto = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.codificar(secreto)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Base32.decodificar("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(secreto);
    }

    @Test
    @DisplayName("HU-17 R54: 20 bytes codifican y decodifican ida y vuelta en 32 caracteres")
    void idaYVuelta() {
        var datos = new byte[20];
        for (int i = 0; i < datos.length; i++) {
            datos[i] = (byte) (i * 37 - 128);
        }

        var texto = Base32.codificar(datos);

        assertThat(texto).hasSize(32).matches("^[A-Z2-7]+$");
        assertThat(Base32.decodificar(texto)).isEqualTo(datos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC0", "ABC1", "ABC8", "ABC=", "abc", "AB-CD"})
    @DisplayName("HU-17 R17: esBase32 rechaza 0, 1, 8, '=', minúsculas y guiones")
    void esBase32RechazaCaracteresFuera(String texto) {
        assertThat(Base32.esBase32(texto)).isFalse();
    }

    @Test
    @DisplayName("HU-17 R17: esBase32 rechaza el vacío y el nulo, y acepta A–Z2–7")
    void esBase32VacioYValido() {
        assertThat(Base32.esBase32("")).isFalse();
        assertThat(Base32.esBase32(null)).isFalse();
        assertThat(Base32.esBase32("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")).isTrue();
    }

    @Test
    @DisplayName("HU-17 R51: decodificar un texto que no es Base32 lanza IllegalArgumentException")
    void decodificarInvalidoFalla() {
        assertThatThrownBy(() -> Base32.decodificar("MZ1W6")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decodificar("MY==")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decodificar(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
