package pe.edu.ulima.espacioya.busqueda.dto;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

class CrearResenaDTOTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RESERVA = "\"reservaId\":90,";
    private static final String CALIFICACION = "\"calificacion\":4,";
    private static final String COMENTARIO = "\"comentario\":\"Buena cancha\"";

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

    private static CrearResenaDTO leer(String campos) {
        return JSON.readValue("{" + campos + "}", CrearResenaDTO.class);
    }

    private static Set<String> mensajes(CrearResenaDTO dto, String campo) {
        return validador.validate(dto).stream()
                .filter(violacion -> violacion.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("HU-24 R15, R16, R17: un cuerpo válido no tiene errores")
    void cuerpoValido() {
        var dto = leer(RESERVA + CALIFICACION + COMENTARIO);

        assertThat(validador.validate(dto)).isEmpty();
        assertThat(dto).isEqualTo(new CrearResenaDTO(90L, 4, "Buena cancha"));
    }

    @Test
    @DisplayName("HU-24 R3: los campos extra del cuerpo se descartan")
    void camposExtraDescartados() {
        var dto = leer("\"usuarioId\":1,\"espacioId\":2,\"estado\":\"OCULTA\"," + RESERVA + CALIFICACION + COMENTARIO);

        assertThat(dto).isEqualTo(new CrearResenaDTO(90L, 4, "Buena cancha"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"reservaId\":null,", "\"reservaId\":\"90\",", "\"reservaId\":90.5,",
            "\"reservaId\":0,", "\"reservaId\":-1,", "\"reservaId\":9223372036854775808,", "\"reservaId\":[90],",
            "\"reservaId\":{\"id\":90},"})
    @DisplayName("HU-24 R15: reservaId ausente, de otro tipo, no positivo o fuera de long falla en su campo")
    void reservaInvalida(String campo) {
        assertThat(mensajes(leer(campo + CALIFICACION + COMENTARIO), "reservaId"))
                .containsExactly(CrearResenaDTO.M_RESERVA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"calificacion\":null,", "\"calificacion\":\"4\",", "\"calificacion\":4.5,",
            "\"calificacion\":4.0,", "\"calificacion\":0,", "\"calificacion\":6,", "\"calificacion\":-3,",
            "\"calificacion\":2147483648,", "\"calificacion\":true,"})
    @DisplayName("HU-24 R16: calificacion ausente, de otro tipo o fuera de 1 a 5 falla en su campo")
    void calificacionInvalida(String campo) {
        assertThat(mensajes(leer(RESERVA + campo + COMENTARIO), "calificacion"))
                .containsExactly(CrearResenaDTO.M_CALIFICACION);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("HU-24 R16: de 1 a 5 son calificaciones válidas")
    void calificacionValida(int valor) {
        assertThat(mensajes(leer(RESERVA + "\"calificacion\":" + valor + "," + COMENTARIO), "calificacion")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"sobra\":1", "\"comentario\":null", "\"comentario\":123", "\"comentario\":\"\"",
            "\"comentario\":\"   \"", "\"comentario\":[\"a\"]"})
    @DisplayName("HU-24 R17: comentario ausente, de otro tipo o en blanco falla en su campo")
    void comentarioInvalido(String campo) {
        assertThat(mensajes(leer(RESERVA + CALIFICACION + campo), "comentario"))
                .containsExactly(CrearResenaDTO.M_COMENTARIO);
    }

    @Test
    @DisplayName("HU-24 R17: 500 caracteres son válidos y 501 no")
    void largoDelComentario() {
        assertThat(mensajes(new CrearResenaDTO(90L, 4, "a".repeat(500)), "comentario")).isEmpty();
        assertThat(mensajes(new CrearResenaDTO(90L, 4, "a".repeat(501)), "comentario"))
                .containsExactly(CrearResenaDTO.M_COMENTARIO);
    }
}
