package pe.edu.ulima.espacioya.espacios.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BloqueoFechaDTOTest {

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

    private static Set<String> mensajes(String fecha) {
        return validador.validate(new BloqueoFechaDTO(fecha)).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("HU-08 R12, R38: fecha ausente, vacía o sin la forma AAAA-MM-DD es inválida")
    void rechazaFechaMalEscrita() {
        assertThat(mensajes(null)).containsExactly(BloqueoFechaDTO.M_FECHA);
        assertThat(mensajes("")).containsExactly(BloqueoFechaDTO.M_FECHA);
        assertThat(mensajes("07/11/2026")).containsExactly(BloqueoFechaDTO.M_FECHA);
    }

    @Test
    @DisplayName("HU-08 R10: una fecha AAAA-MM-DD pasa la validación de forma")
    void aceptaFechaBienEscrita() {
        assertThat(mensajes("2026-11-07")).isEmpty();
    }
}
