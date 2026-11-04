package pe.edu.ulima.espacioya.pagos.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuardarCuentaBancariaDTOTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    private static GuardarCuentaBancariaDTO valido() {
        return new GuardarCuentaBancariaDTO("Banco Andino", "00112233445566778899", true, "Segura123!");
    }

    private static List<String> campos(GuardarCuentaBancariaDTO dto) {
        return FABRICA.getValidator().validate(dto).stream()
                .map(v -> v.getPropertyPath().toString()).distinct().sorted().collect(Collectors.toList());
    }

    @Test
    @DisplayName("HU-18 R10: un cuerpo válido no tiene errores")
    void valido_sinErrores() {
        assertThat(campos(valido())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "B"})
    @DisplayName("HU-18 R10: un banco en blanco o de menos de 2 caracteres falla en el campo banco")
    void bancoCorto(String banco) {
        var dto = new GuardarCuentaBancariaDTO(banco, "00112233445566778899", true, "x");

        assertThat(campos(dto)).containsExactly("banco");
    }

    @Test
    @DisplayName("HU-18 R10: un banco de más de 60 caracteres falla en el campo banco con el mensaje exacto")
    void bancoLargo() {
        var dto = new GuardarCuentaBancariaDTO("B".repeat(61), "00112233445566778899", true, "x");

        var errores = FABRICA.getValidator().validate(dto);

        assertThat(errores).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("banco");
            assertThat(v.getMessage()).isEqualTo(GuardarCuentaBancariaDTO.MENSAJE_BANCO)
                    .isEqualTo("Ingresa el nombre del banco (de 2 a 60 caracteres).");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123456789", "123456789012345678901", "1234-5678-90", "12345abcde67", "1234 567890"})
    @DisplayName("HU-18 R10: un número que no son de 10 a 20 dígitos falla en el campo numero")
    void numeroInvalido(String numero) {
        var dto = new GuardarCuentaBancariaDTO("Banco Andino", numero, true, "x");

        assertThat(campos(dto)).contains("numero").doesNotContain("banco");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "12345678901234567890"})
    @DisplayName("HU-18 R10: 10 y 20 dígitos son válidos")
    void numeroEnLosLimites(String numero) {
        assertThat(campos(new GuardarCuentaBancariaDTO("Banco Andino", numero, true, "x"))).isEmpty();
    }

    @Test
    @DisplayName("HU-18 R10: declaraTitularidad distinto de true falla en su campo con el mensaje exacto")
    void sinDeclaracion() {
        var dto = new GuardarCuentaBancariaDTO("Banco Andino", "00112233445566778899", false, "x");

        var errores = FABRICA.getValidator().validate(dto);

        assertThat(errores).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("declaraTitularidad");
            assertThat(v.getMessage()).isEqualTo("Confirma que la cuenta está a tu nombre o al de tu RUC.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("HU-18 R6: una contraseña en blanco falla en el campo contrasena con el mensaje exacto")
    void contrasenaEnBlanco(String contrasena) {
        var dto = new GuardarCuentaBancariaDTO("Banco Andino", "00112233445566778899", true, contrasena);

        var errores = FABRICA.getValidator().validate(dto);

        assertThat(errores).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("contrasena");
            assertThat(v.getMessage()).isEqualTo("Ingresa tu contraseña para confirmar.");
        });
    }

    @Test
    @DisplayName("HU-18 R6: una contraseña ausente falla en el campo contrasena")
    void contrasenaNula() {
        var dto = new GuardarCuentaBancariaDTO("Banco Andino", "00112233445566778899", true, null);

        assertThat(campos(dto)).containsExactly("contrasena");
    }

    @Test
    @DisplayName("HU-18 R10: varios campos inválidos producen un error por cada uno")
    void variosErrores() {
        var dto = new GuardarCuentaBancariaDTO("", "abc", false, "x");

        assertThat(campos(dto)).contains("banco", "numero", "declaraTitularidad");
    }

    @Test
    @DisplayName("HU-18 R22: toString no muestra el número ni la contraseña")
    void toStringSinSecretos() {
        var texto = valido().toString();

        assertThat(texto).doesNotContain("00112233445566778899").doesNotContain("8899").doesNotContain("Segura123!");
        assertThat(texto).contains("numero=***").contains("contrasena=***");
    }

    @Test
    @DisplayName("HU-18 R19, R22: CuentaBancariaDTO y MiCuentaBancariaDTO solo exponen el número enmascarado")
    void respuestasSoloEnmascaradas() {
        var cuenta = new CuentaBancariaDTO("Banco Andino", "****8899", "Canchas SAC",
                pe.edu.ulima.espacioya.pagos.domain.EstadoCuentaBancaria.DISPONIBLE, null);

        assertThat(CuentaBancariaDTO.class.getRecordComponents()).extracting("name")
                .containsExactly("banco", "numeroEnmascarado", "titular", "estado", "retenidaHasta");
        assertThat(new MiCuentaBancariaDTO(true, cuenta).toString()).doesNotContain("0011223344");
    }
}
