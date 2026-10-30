package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SeleccionBloquesTest {

    private static List<Integer> veinticuatroHoras() {
        return IntStream.range(0, 24).boxed().toList();
    }

    static Stream<List<Integer>> validas() {
        return Stream.of(List.of(14), List.of(15, 14), List.of(14, 15, 16), List.of(0), List.of(23),
                veinticuatroHoras());
    }

    static Stream<List<Integer>> invalidas() {
        var conVeinticinco = IntStream.range(0, 25).boxed().toList();
        return Stream.of(null, List.of(), List.of(14, 16), List.of(14, 14), Arrays.asList((Integer) null),
                Arrays.asList(14, null), List.of(24), List.of(-1), List.of(23, 24), conVeinticinco);
    }

    @ParameterizedTest
    @MethodSource("validas")
    @DisplayName("HU-12 R32: acepta de 1 a 24 horas consecutivas, también en desorden")
    void aceptaSeleccionValida(List<Integer> horas) {
        assertThat(SeleccionBloques.esValida(horas)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidas")
    @DisplayName("HU-12 R32: rechaza nula, vacía, con huecos, repetidas, con nulos, fuera de 0 a 23 o con más de 24")
    void rechazaSeleccionInvalida(List<Integer> horas) {
        assertThat(SeleccionBloques.esValida(horas)).isFalse();
    }

    @Test
    @DisplayName("HU-12 R32: ordena las horas de menor a mayor")
    void ordenaLasHoras() {
        assertThat(SeleccionBloques.ordenadas(List.of(16, 14, 15))).containsExactly(14, 15, 16);
    }
}
