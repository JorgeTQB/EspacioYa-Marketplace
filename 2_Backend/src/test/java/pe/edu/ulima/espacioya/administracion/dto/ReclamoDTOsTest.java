package pe.edu.ulima.espacioya.administracion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

class ReclamoDTOsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    private static <T> Set<String> camposConError(T dto) {
        return validador.validate(dto).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static <T> Set<String> mensajes(T dto) {
        return validador.validate(dto).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private static CrearReclamoDTO crear(String json) {
        return JSON.readValue(json, CrearReclamoDTO.class);
    }

    @Test
    @DisplayName("HU-26 R1, R3: un cuerpo válido no tiene errores e ignora los campos extra")
    void crearValido() {
        var dto = crear("{\"reservaId\":90,\"motivo\":\"OTRO\",\"descripcion\":\"Algo\",\"usuarioId\":5,"
                + "\"arrendadorId\":3,\"estado\":\"RESUELTO_USUARIO\"}");

        assertThat(validador.validate(dto)).isEmpty();
        assertThat(dto).isEqualTo(new CrearReclamoDTO(90L, "OTRO", "Algo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"reservaId\":null,", "\"reservaId\":\"90\",", "\"reservaId\":1.5,",
            "\"reservaId\":0,", "\"reservaId\":-1,", "\"reservaId\":9223372036854775808,"})
    @DisplayName("HU-26 R15: un reservaId ausente, de otro tipo, decimal, no positivo o enorme es inválido")
    void reservaInvalida(String campo) {
        var dto = crear("{" + campo + "\"motivo\":\"OTRO\",\"descripcion\":\"Algo\"}");

        assertThat(camposConError(dto)).containsExactly("reservaId");
        assertThat(mensajes(dto)).containsExactly(CrearReclamoDTO.M_RESERVA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"motivo\":null,", "\"motivo\":\"SUCIO\",", "\"motivo\":\"otro\",",
            "\"motivo\":1,"})
    @DisplayName("HU-26 R16: un motivo fuera del catálogo es inválido")
    void motivoInvalido(String campo) {
        var dto = crear("{\"reservaId\":90," + campo + "\"descripcion\":\"Algo\"}");

        assertThat(camposConError(dto)).containsExactly("motivo");
        assertThat(mensajes(dto)).containsExactly(CrearReclamoDTO.M_MOTIVO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"descripcion\":null", ",\"descripcion\":123", ",\"descripcion\":\"   \""})
    @DisplayName("HU-26 R17: una descripción ausente, de otro tipo o en blanco es inválida")
    void descripcionInvalida(String campo) {
        var dto = crear("{\"reservaId\":90,\"motivo\":\"OTRO\"" + campo + "}");

        assertThat(camposConError(dto)).containsExactly("descripcion");
        assertThat(mensajes(dto)).containsExactly(CrearReclamoDTO.M_DESCRIPCION);
    }

    @Test
    @DisplayName("HU-26 R17: 1000 caracteres valen y 1001 no")
    void largoDescripcion() {
        assertThat(validador.validate(new CrearReclamoDTO(90L, "OTRO", "a".repeat(1000)))).isEmpty();
        assertThat(camposConError(new CrearReclamoDTO(90L, "OTRO", "a".repeat(1001)))).containsExactly("descripcion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"texto\":null}", "{\"texto\":5}", "{\"texto\":\" \"}"})
    @DisplayName("HU-26 R28: un texto de mensaje ausente, de otro tipo o en blanco es inválido")
    void textoInvalido(String json) {
        var dto = JSON.readValue(json, NuevoMensajeDTO.class);

        assertThat(mensajes(dto)).containsExactly(NuevoMensajeDTO.M_TEXTO);
    }

    @Test
    @DisplayName("HU-26 R28: un mensaje de 1001 caracteres es inválido y uno de 1000 no")
    void largoTexto() {
        assertThat(validador.validate(new NuevoMensajeDTO("a".repeat(1000)))).isEmpty();
        assertThat(mensajes(new NuevoMensajeDTO("a".repeat(1001)))).containsExactly(NuevoMensajeDTO.M_TEXTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"aFavorDe\":null,", "\"aFavorDe\":\"ADMIN\",", "\"aFavorDe\":\"usuario\",",
            "\"aFavorDe\":1,"})
    @DisplayName("HU-26 R53: aFavorDe ausente o distinto de USUARIO y ARRENDADOR es inválido")
    void aFavorInvalido(String campo) {
        var dto = JSON.readValue("{" + campo + "\"resolucion\":\"Ok\"}", ResolucionReclamoDTO.class);

        assertThat(camposConError(dto)).containsExactly("aFavorDe");
        assertThat(mensajes(dto)).containsExactly(ResolucionReclamoDTO.M_A_FAVOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"resolucion\":null", ",\"resolucion\":3", ",\"resolucion\":\"  \""})
    @DisplayName("HU-26 R52: una resolución ausente, de otro tipo o en blanco es inválida")
    void resolucionInvalida(String campo) {
        var dto = JSON.readValue("{\"aFavorDe\":\"ARRENDADOR\"" + campo + "}", ResolucionReclamoDTO.class);

        assertThat(camposConError(dto)).containsExactly("resolucion");
        assertThat(mensajes(dto)).containsExactly(ResolucionReclamoDTO.M_RESOLUCION);
    }

    @Test
    @DisplayName("HU-26 R52: 500 caracteres valen y 501 no")
    void largoResolucion() {
        assertThat(validador.validate(new ResolucionReclamoDTO("ARRENDADOR", null, null, "a".repeat(500)))).isEmpty();
        assertThat(camposConError(new ResolucionReclamoDTO("ARRENDADOR", null, null, "a".repeat(501))))
                .containsExactly("resolucion");
    }

    @Test
    @DisplayName("HU-26 R46: montoReembolso conserva su escala exacta y un texto o booleano se leen como null")
    void montoComoNumero() {
        var conEscala = JSON.readValue("{\"aFavorDe\":\"USUARIO\",\"reembolso\":\"PARCIAL\",\"montoReembolso\":10.005,"
                + "\"resolucion\":\"Ok\"}", ResolucionReclamoDTO.class);
        var texto = JSON.readValue("{\"aFavorDe\":\"USUARIO\",\"montoReembolso\":\"10\",\"resolucion\":\"Ok\"}",
                ResolucionReclamoDTO.class);
        var booleano = JSON.readValue("{\"aFavorDe\":\"USUARIO\",\"montoReembolso\":true,\"resolucion\":\"Ok\"}",
                ResolucionReclamoDTO.class);

        assertThat(conEscala.montoReembolso()).isEqualTo(new BigDecimal("10.005"));
        assertThat(conEscala.reembolso()).isEqualTo("PARCIAL");
        assertThat(texto.montoReembolso()).isNull();
        assertThat(booleano.montoReembolso()).isNull();
    }
}
