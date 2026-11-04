package pe.edu.ulima.espacioya.administracion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MotivoSuspensionDTOTest {

    private static final String M_MOTIVO = "Escribe el motivo de la suspensión (hasta 500 caracteres).";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static List<String> errores(String motivo) {
        return validador.validate(new MotivoSuspensionDTO(motivo)).stream()
                .map(violacion -> violacion.getPropertyPath() + ": " + violacion.getMessage())
                .distinct()
                .toList();
    }

    @Test
    @DisplayName("HU-09 R19: sin motivo, vacío o solo espacios → error en el campo motivo con el mensaje fijo")
    void motivoAusenteOEnBlanco() {
        for (var motivo : new String[] {null, "", "   ", "\t\n"}) {
            assertThat(errores(motivo)).containsExactly("motivo: " + M_MOTIVO);
        }
    }

    @Test
    @DisplayName("HU-09 R19: 500 caracteres son válidos y 501 dan el error en el campo motivo")
    void longitudMaxima() {
        assertThat(errores("a".repeat(500))).isEmpty();
        assertThat(errores("a".repeat(501))).containsExactly("motivo: " + M_MOTIVO);
        assertThat(errores("  " + "a".repeat(500) + "  ")).isEmpty();
    }

    @Test
    @DisplayName("HU-09 R17, R19: el motivo se recorta al inicio y al final antes de validarlo")
    void recortaElMotivo() {
        var dto = new MotivoSuspensionDTO("  Publicó datos falsos.  ");

        assertThat(dto.motivo()).isEqualTo("Publicó datos falsos.");
        assertThat(validador.validate(dto)).extracting(ConstraintViolation::getMessage).isEmpty();
    }
}
