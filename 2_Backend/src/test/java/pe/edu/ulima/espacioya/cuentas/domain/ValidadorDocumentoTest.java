package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidadorDocumentoTest {

    private static String dni(String numero) {
        return ValidadorDocumento.error(TipoDocumento.DNI, ValidadorDocumento.normalizar(numero)).orElse(null);
    }

    private static String ruc(String numero) {
        return ValidadorDocumento.error(TipoDocumento.RUC, ValidadorDocumento.normalizar(numero)).orElse(null);
    }

    @Test
    @DisplayName("HU-02 R17: un número ausente o vacío tras normalizar pide el número de documento")
    void numeroFaltante() {
        assertThat(dni(null)).isEqualTo("Ingresa tu número de documento.");
        assertThat(dni("   ")).isEqualTo("Ingresa tu número de documento.");
        assertThat(ruc("")).isEqualTo("Ingresa tu número de documento.");
    }

    @Test
    @DisplayName("HU-02 R17: normalizar quita los espacios de los extremos")
    void normalizaEspacios() {
        assertThat(ValidadorDocumento.normalizar("  12345678 ")).isEqualTo("12345678");
        assertThat(ValidadorDocumento.normalizar(null)).isNull();
        assertThat(dni(" 12345678 ")).isNull();
    }

    @Test
    @DisplayName("HU-02 R18: el DNI acepta exactamente 8 dígitos")
    void dniDeOchoDigitos() {
        assertThat(dni("12345678")).isNull();
        assertThat(dni("1234567")).isEqualTo("El DNI debe tener 8 dígitos.");
        assertThat(dni("123456789")).isEqualTo("El DNI debe tener 8 dígitos.");
        assertThat(dni("1234567A")).isEqualTo("El DNI debe tener 8 dígitos.");
        assertThat(dni("1234 678")).isEqualTo("El DNI debe tener 8 dígitos.");
        assertThat(dni("20100070970")).isEqualTo("El DNI debe tener 8 dígitos.");
    }

    @Test
    @DisplayName("HU-02 R19: el RUC exige exactamente 11 dígitos")
    void rucDeOnceDigitos() {
        assertThat(ruc("2010007097")).isEqualTo("El RUC debe tener 11 dígitos.");
        assertThat(ruc("201000709701")).isEqualTo("El RUC debe tener 11 dígitos.");
        assertThat(ruc("2010007097A")).isEqualTo("El RUC debe tener 11 dígitos.");
        assertThat(ruc("12345678")).isEqualTo("El RUC debe tener 11 dígitos.");
    }

    @Test
    @DisplayName("HU-02 R20: el RUC debe empezar con 10, 15, 17 o 20")
    void prefijoDelRuc() {
        assertThat(ruc("11000000002")).isEqualTo("El RUC debe empezar con 10, 15, 17 o 20.");
        assertThat(ruc("30000000007")).isEqualTo("El RUC debe empezar con 10, 15, 17 o 20.");
        assertThat(ruc("10460278975")).isNull();
        assertThat(ruc("15100000005")).isNull();
        assertThat(ruc("17100000008")).isNull();
        assertThat(ruc("20131312955")).isNull();
    }

    @Test
    @DisplayName("HU-02 R21: el RUC valida el dígito verificador módulo 11")
    void digitoVerificador() {
        assertThat(ruc("20100070970")).isNull();
        assertThat(ruc("20100070971")).isEqualTo("El RUC no es válido. Revisa que lo hayas escrito bien.");
        assertThat(ruc("20131312954")).isEqualTo("El RUC no es válido. Revisa que lo hayas escrito bien.");
    }

    @Test
    @DisplayName("HU-02 R21: un resto de 10 da dígito 0 y uno de 11 da dígito 1")
    void restosEspeciales() {
        // 20100070970: suma 89, 89 mod 11 = 1 → 11 − 1 = 10 → 0.
        assertThat(ruc("20100070970")).isNull();
        // 20100000131: suma 22, 22 mod 11 = 0 → 11 − 0 = 11 → 1.
        assertThat(ruc("20100000131")).isNull();
        assertThat(ruc("20100000130")).isEqualTo("El RUC no es válido. Revisa que lo hayas escrito bien.");
    }
}
