package pe.edu.ulima.espacioya.pagos.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MarcarLiquidacionPagadaDTOTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "123", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "OP 1234", " 1234", "1234 ",
            "OP_1234", "OP.1234",
            "OP/1234", "ÑANDÚ-12", "1234;DROP"})
    @DisplayName("HU-19 R30: nulo, vacío, de 3 o 31 caracteres, con espacios o caracteres no permitidos falla en "
            + "numeroOperacion con el mensaje exacto")
    void numeroInvalido(String numero) {
        var errores = FABRICA.getValidator().validate(new MarcarLiquidacionPagadaDTO(numero));

        assertThat(errores).singleElement().satisfies(error -> {
            assertThat(error.getPropertyPath().toString()).isEqualTo("numeroOperacion");
            assertThat(error.getMessage()).isEqualTo(MarcarLiquidacionPagadaDTO.MENSAJE_NUMERO_OPERACION)
                    .isEqualTo("Ingresa el número de operación: de 4 a 30 letras, dígitos o guiones.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "OP-00123456", "abc-DEF-123", "----"})
    @DisplayName("HU-19 R30: de 4 a 30 letras, dígitos o guiones es válido")
    void numeroValido(String numero) {
        assertThat(FABRICA.getValidator().validate(new MarcarLiquidacionPagadaDTO(numero))).isEmpty();
    }

    @Test
    @DisplayName("HU-19 R30: 30 caracteres es el máximo válido")
    void treintaCaracteres() {
        assertThat(FABRICA.getValidator().validate(new MarcarLiquidacionPagadaDTO("9".repeat(30)))).isEmpty();
    }
}
