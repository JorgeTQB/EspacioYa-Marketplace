package pe.edu.ulima.espacioya.espacios.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;

class HorarioDTOTest {

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

    // La ruta de un elemento nulo de la lista termina en ".<list element>"; Spring la reporta como franjas[0].
    private static Map<String, String> errores(HorarioDTO horario) {
        return validador.validate(horario).stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString().replace(".<list element>", ""),
                ConstraintViolation::getMessage, (a, b) -> a));
    }

    private static HorarioDTO horario(FranjaDTO... franjas) {
        return new HorarioDTO(Arrays.asList(franjas));
    }

    private static Map<String, String> erroresDe(Integer dia, Integer inicio, Integer fin) {
        return errores(horario(new FranjaDTO(dia, inicio, fin)));
    }

    @Test
    @DisplayName("HU-08 R4: el día de la semana va de 1 a 7")
    void validaDia() {
        assertThat(erroresDe(0, 8, 10)).containsExactlyEntriesOf(Map.of("franjas[0].diaSemana", FranjaDTO.M_DIA));
        assertThat(erroresDe(1, 8, 10)).isEmpty();
        assertThat(erroresDe(7, 8, 10)).isEmpty();
        assertThat(erroresDe(8, 8, 10)).containsExactlyEntriesOf(Map.of("franjas[0].diaSemana", FranjaDTO.M_DIA));
    }

    @Test
    @DisplayName("HU-08 R4: la hora de inicio va de 0 a 23")
    void validaHoraInicio() {
        assertThat(erroresDe(1, -1, 10))
                .containsExactlyEntriesOf(Map.of("franjas[0].horaInicio", FranjaDTO.M_HORA_INICIO));
        assertThat(erroresDe(1, 0, 10)).isEmpty();
        assertThat(erroresDe(1, 23, 24)).isEmpty();
        assertThat(erroresDe(1, 24, 24))
                .containsExactlyEntriesOf(Map.of("franjas[0].horaInicio", FranjaDTO.M_HORA_INICIO));
    }

    @Test
    @DisplayName("HU-08 R4: la hora de fin va de 1 a 24")
    void validaHoraFin() {
        assertThat(erroresDe(1, 0, 0)).containsExactlyEntriesOf(Map.of("franjas[0].horaFin", FranjaDTO.M_HORA_FIN));
        assertThat(erroresDe(1, 0, 1)).isEmpty();
        assertThat(erroresDe(1, 0, 24)).isEmpty();
        assertThat(erroresDe(1, 0, 25)).containsExactlyEntriesOf(Map.of("franjas[0].horaFin", FranjaDTO.M_HORA_FIN));
    }

    @Test
    @DisplayName("HU-08 R4: cada campo nulo de la franja tiene su error")
    void rechazaNulos() {
        assertThat(erroresDe(null, null, null)).containsOnly(
                Map.entry("franjas[0].diaSemana", FranjaDTO.M_DIA),
                Map.entry("franjas[0].horaInicio", FranjaDTO.M_HORA_INICIO),
                Map.entry("franjas[0].horaFin", FranjaDTO.M_HORA_FIN));
    }

    @Test
    @DisplayName("HU-08 R4: un elemento nulo de la lista se reporta en franjas[i]")
    void rechazaElementoNulo() {
        var franjas = new ArrayList<FranjaDTO>();
        franjas.add(null);

        assertThat(errores(new HorarioDTO(franjas))).containsExactlyEntriesOf(Map.of("franjas[0]", FranjaDTO.M_FRANJA));
    }

    @Test
    @DisplayName("HU-08 R3, R4: franjas ausente es un error; vacía y hasta 168 franjas son válidas")
    void validaLaLista() {
        var franja = new FranjaDTO(1, 8, 9);

        assertThat(errores(new HorarioDTO(null))).containsExactlyEntriesOf(Map.of("franjas", HorarioDTO.M_FRANJAS));
        assertThat(errores(new HorarioDTO(List.of()))).isEmpty();
        assertThat(errores(new HorarioDTO(IntStream.range(0, 168).mapToObj(i -> franja).toList()))).isEmpty();
        assertThat(errores(new HorarioDTO(IntStream.range(0, 169).mapToObj(i -> franja).toList())))
                .containsExactlyEntriesOf(Map.of("franjas", HorarioDTO.M_FRANJAS));
    }

    @Test
    @DisplayName("HU-08 R1: aFranjas conserva el orden recibido y de() ordena por día e inicio")
    void conviertenSinPerderElOrden() {
        var recibido = horario(new FranjaDTO(6, 8, 13), new FranjaDTO(1, 8, 22));

        assertThat(recibido.aFranjas()).containsExactly(new FranjaHoraria(6, 8, 13), new FranjaHoraria(1, 8, 22));
        assertThat(HorarioDTO.de(recibido.aFranjas()).franjas())
                .containsExactly(new FranjaDTO(1, 8, 22), new FranjaDTO(6, 8, 13));
    }
}
