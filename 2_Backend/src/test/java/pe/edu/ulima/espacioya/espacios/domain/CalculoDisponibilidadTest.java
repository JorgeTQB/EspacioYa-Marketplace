package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalculoDisponibilidadTest {

    // 2026-11-02 es lunes.
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDateTime ANTES = LocalDateTime.of(2026, 10, 28, 15, 0);
    private static final List<FranjaHoraria> FRANJAS = List.of(new FranjaHoraria(1, 14, 16),
            new FranjaHoraria(1, 8, 10));

    @Test
    @DisplayName("HU-08 R22: un bloque por hora de las franjas del día, ascendente y con su hora de fin")
    void unBloquePorHora() {
        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, FRANJAS, false, Set.of(), ANTES);

        assertThat(bloques).extracting(Bloque::horaInicio).containsExactly(8, 9, 14, 15);
        assertThat(bloques).extracting(Bloque::horaFin).containsExactly(9, 10, 15, 16);
    }

    @Test
    @DisplayName("HU-08 R22: solo cuentan las franjas del día de la semana de la fecha")
    void ignoraOtrosDias() {
        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, List.of(new FranjaHoraria(2, 8, 10)), false,
                Set.of(), ANTES);

        assertThat(bloques).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R23: una fecha bloqueada tiene todos sus bloques BLOQUEADO, aun con reservas")
    void bloqueadaGanaATodo() {
        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, FRANJAS, true, Set.of(8, 14), ANTES);

        assertThat(bloques).extracting(Bloque::estado).containsOnly(EstadoBloque.BLOQUEADO);
    }

    @Test
    @DisplayName("HU-08 R24: una hora retenida por una reserva activa es RESERVADO")
    void reservadaEsReservado() {
        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, FRANJAS, false, Set.of(9), ANTES);

        assertThat(bloques).extracting(Bloque::estado).containsExactly(EstadoBloque.LIBRE, EstadoBloque.RESERVADO,
                EstadoBloque.LIBRE, EstadoBloque.LIBRE);
    }

    @Test
    @DisplayName("HU-08 R25: hoy a las 10:30 las horas 8, 9 y 10 ya pasaron y la 11 sigue libre")
    void horasEmpezadasSonPasado() {
        var franjas = List.of(new FranjaHoraria(1, 8, 12));

        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, franjas, false, Set.of(),
                LUNES.atTime(10, 30));

        assertThat(bloques).extracting(Bloque::estado).containsExactly(EstadoBloque.PASADO, EstadoBloque.PASADO,
                EstadoBloque.PASADO, EstadoBloque.LIBRE);
    }

    @Test
    @DisplayName("HU-08 R25: a las 10:00 exactas el bloque de las 10 ya pasó; una reserva gana a PASADO")
    void enPuntoEsPasado() {
        var franjas = List.of(new FranjaHoraria(1, 9, 12));

        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, franjas, false, Set.of(9), LUNES.atTime(10, 0));

        assertThat(bloques).extracting(Bloque::estado).containsExactly(EstadoBloque.RESERVADO, EstadoBloque.PASADO,
                EstadoBloque.LIBRE);
    }

    @Test
    @DisplayName("HU-08 R26, R28: fecha futura sin bloqueo ni reservas, todos LIBRE")
    void futuraTodoLibre() {
        var bloques = CalculoDisponibilidad.bloquesDelDia(LUNES, FRANJAS, false, Set.of(), ANTES);

        assertThat(bloques).extracting(Bloque::estado).containsOnly(EstadoBloque.LIBRE);
    }
}
