package pe.edu.ulima.espacioya.reservas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

class CrearReservaDTOTest {

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

    private static CrearReservaDTO leer(String cuerpo) {
        return JSON.readValue(cuerpo, CrearReservaDTO.class);
    }

    private static CrearReservaDTO conEspacio(String valor) {
        return leer("{" + valor + "\"fecha\":\"2026-11-07\",\"horas\":[14]}");
    }

    private static CrearReservaDTO conFecha(String valor) {
        return leer("{\"espacioId\":5," + valor + "\"horas\":[14]}");
    }

    private static CrearReservaDTO conHoras(String valor) {
        return leer("{\"espacioId\":5,\"fecha\":\"2026-11-07\"" + valor + "}");
    }

    private static Map<String, Set<String>> errores(CrearReservaDTO dto) {
        return validador.validate(dto).stream().collect(Collectors.groupingBy(
                violacion -> violacion.getPropertyPath().toString(),
                Collectors.mapping(ConstraintViolation::getMessage, Collectors.toSet())));
    }

    @Test
    @DisplayName("HU-12 R1: una solicitud bien formada pasa la validación y conserva las horas en su orden")
    void solicitudValida() {
        var dto = conHoras(",\"horas\":[15,14]");

        assertThat(errores(dto)).isEmpty();
        assertThat(dto.espacioId()).isEqualTo(5L);
        assertThat(dto.fecha()).isEqualTo("2026-11-07");
        assertThat(dto.horas()).containsExactly(15, 14);
    }

    static Stream<String> espaciosInvalidos() {
        return Stream.of("", "\"espacioId\":null,", "\"espacioId\":0,", "\"espacioId\":-1,", "\"espacioId\":5.5,",
                "\"espacioId\":\"5\",", "\"espacioId\":true,", "\"espacioId\":[5],", "\"espacioId\":{\"id\":5},",
                "\"espacioId\":123456789012345678901234567890,");
    }

    @ParameterizedTest
    @MethodSource("espaciosInvalidos")
    @DisplayName("HU-12 R29: espacioId ausente o que no es un entero JSON positivo es un error en ese campo")
    void rechazaEspacioInvalido(String valor) {
        assertThat(errores(conEspacio(valor))).containsOnly(Map.entry("espacioId", Set.of(CrearReservaDTO.M_ESPACIO)));
    }

    static Stream<String> fechasInvalidas() {
        return Stream.of("", "\"fecha\":null,", "\"fecha\":\"\",", "\"fecha\":\"   \",", "\"fecha\":\"07/11/2026\",",
                "\"fecha\":20261107,", "\"fecha\":{\"dia\":7},", "\"fecha\":[\"2026-11-07\"],");
    }

    @ParameterizedTest
    @MethodSource("fechasInvalidas")
    @DisplayName("HU-12 R30: fecha ausente, vacía, sin la forma AAAA-MM-DD o de otro tipo es un error en ese campo")
    void rechazaFechaInvalida(String valor) {
        assertThat(errores(conFecha(valor))).containsOnly(Map.entry("fecha", Set.of(CrearReservaDTO.M_FECHA)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"horas\":null", ",\"horas\":[]", ",\"horas\":14", ",\"horas\":\"14\"",
        ",\"horas\":{\"a\":14}"})
    @DisplayName("HU-12 R32: horas ausente, vacía o que no es un arreglo es un error en ese campo")
    void rechazaHorasSinForma(String valor) {
        assertThat(errores(conHoras(valor))).containsOnly(Map.entry("horas", Set.of(CrearReservaDTO.M_HORAS)));
    }

    @Test
    @DisplayName("HU-12 R32: un decimal, un texto o un nulo dentro de horas se leen como elemento null")
    void elementosQueNoSonEnterosSeLeenComoNull() {
        assertThat(conHoras(",\"horas\":[14.5]").horas()).isEqualTo(Arrays.asList((Integer) null));
        assertThat(conHoras(",\"horas\":[\"14\"]").horas()).isEqualTo(Arrays.asList((Integer) null));
        assertThat(conHoras(",\"horas\":[14,null]").horas()).isEqualTo(Arrays.asList(14, null));
        assertThat(conHoras(",\"horas\":[14,[15],16]").horas()).isEqualTo(Arrays.asList(14, null, 16));
        assertThat(conHoras(",\"horas\":[99999999999]").horas()).isEqualTo(Arrays.asList((Integer) null));
    }

    @Test
    @DisplayName("HU-12 R5: usuarioId, id, estado, montoTotal, precioHora y expiraEn del cliente se ignoran")
    void ignoraCamposDesconocidos() {
        var dto = leer("{\"espacioId\":5,\"fecha\":\"2026-11-07\",\"horas\":[14],\"usuarioId\":99,\"id\":1,"
                + "\"estado\":\"CONFIRMADA\",\"montoTotal\":0.01,\"precioHora\":0.01,\"expiraEn\":\"2030-01-01\"}");

        assertThat(errores(dto)).isEmpty();
        assertThat(dto).isEqualTo(new CrearReservaDTO(5L, "2026-11-07", List.of(14)));
    }
}
