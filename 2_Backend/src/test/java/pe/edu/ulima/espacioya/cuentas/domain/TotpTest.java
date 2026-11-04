package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TotpTest {

    private static final byte[] SECRETO = Base32.decodificar("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    private static final Instant AHORA = Instant.ofEpochSecond(1_111_111_111L);
    private static final long PASO = 1_111_111_111L / 30;

    @ParameterizedTest
    @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924",
        "2000000000,279037", "20000000000,353130"})
    @DisplayName("HU-17 R51: produce los 6 últimos dígitos de los vectores SHA-1 del Apéndice B de RFC 6238")
    void vectoresRfc6238(long segundos, String esperado) {
        assertThat(Totp.codigo(SECRETO, Totp.pasoDe(Instant.ofEpochSecond(segundos)))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("HU-17 R51: el paso es floor(segundos epoch / 30)")
    void pasoDe() {
        assertThat(Totp.pasoDe(Instant.ofEpochSecond(0))).isZero();
        assertThat(Totp.pasoDe(Instant.ofEpochSecond(29))).isZero();
        assertThat(Totp.pasoDe(Instant.ofEpochSecond(30))).isEqualTo(1);
        assertThat(Totp.pasoDe(Instant.ofEpochSecond(59, 999_999_999))).isEqualTo(1);
        assertThat(Totp.pasoDe(AHORA)).isEqualTo(PASO);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @DisplayName("HU-17 R52: acepta el código del paso actual, del anterior y del siguiente, y devuelve ese paso")
    void aceptaToleranciaDeUnPaso(long desplazamiento) {
        var codigo = Totp.codigo(SECRETO, PASO + desplazamiento);

        assertThat(Totp.pasoAceptado(SECRETO, codigo, AHORA, null)).hasValue(PASO + desplazamiento);
    }

    @ParameterizedTest
    @ValueSource(longs = {-3, -2, 2, 3})
    @DisplayName("HU-17 R53: rechaza el código de un paso a 2 o más pasos del actual")
    void rechazaDosPasosOMas(long desplazamiento) {
        var codigo = Totp.codigo(SECRETO, PASO + desplazamiento);

        assertThat(Totp.pasoAceptado(SECRETO, codigo, AHORA, null)).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R15: rechaza un código cuyo paso es igual o anterior a ultimo_paso")
    void rechazaPasoYaUsado() {
        var actual = Totp.codigo(SECRETO, PASO);
        var anterior = Totp.codigo(SECRETO, PASO - 1);

        assertThat(Totp.pasoAceptado(SECRETO, actual, AHORA, PASO)).isEmpty();
        assertThat(Totp.pasoAceptado(SECRETO, anterior, AHORA, PASO - 1)).isEmpty();
        assertThat(Totp.pasoAceptado(SECRETO, actual, AHORA, PASO - 1)).hasValue(PASO);
    }

    @Test
    @DisplayName("HU-17 R14, R15: con ultimo_paso = actual sigue aceptando el código del paso siguiente")
    void aceptaPasoSiguienteTrasUltimo() {
        var siguiente = Totp.codigo(SECRETO, PASO + 1);

        assertThat(Totp.pasoAceptado(SECRETO, siguiente, AHORA, PASO)).hasValue(PASO + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "1234567", "abcdef", "12a456", "ABCDE-FGHIJ", "١٢٣٤٥٦"})
    @DisplayName("HU-17 R15: un texto que no son 6 dígitos ASCII no es un código aceptado")
    void rechazaFormatoInvalido(String codigo) {
        assertThat(Totp.pasoAceptado(SECRETO, codigo, AHORA, null)).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R15: un código nulo no es aceptado")
    void rechazaNulo() {
        assertThat(Totp.pasoAceptado(SECRETO, null, AHORA, null)).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R52: ignora los espacios del código enviado")
    void ignoraEspacios() {
        var codigo = Totp.codigo(SECRETO, PASO);
        var conEspacios = " " + codigo.substring(0, 3) + " " + codigo.substring(3) + " ";

        assertThat(Totp.pasoAceptado(SECRETO, conEspacios, AHORA, null)).hasValue(PASO);
    }
}
