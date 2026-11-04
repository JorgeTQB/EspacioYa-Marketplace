package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservaConHorarioTest {

    private static final Reserva RESERVA =
            Reserva.pendiente(7L, 40L, new BigDecimal("80.50"), 2, Instant.parse("2026-10-28T15:00:00Z"));

    @Test
    @DisplayName("HU-15 R14: el inicio se toma en America/Lima (UTC−5)")
    void instanteDeInicioEnLima() {
        var horario = new ReservaConHorario(RESERVA, LocalDate.of(2026, 10, 30), 10, 12);

        assertThat(horario.instanteDeInicio()).isEqualTo(Instant.parse("2026-10-30T15:00:00Z"));
    }

    @Test
    @DisplayName("HU-15 R14: con horaInicio = 0 el inicio es la medianoche de Lima de esa fecha")
    void instanteDeInicioALaMedianoche() {
        var horario = new ReservaConHorario(RESERVA, LocalDate.of(2026, 11, 2), 0, 1);

        assertThat(horario.instanteDeInicio()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
    }

    @Test
    @DisplayName("HU-19 R8: el fin se toma en America/Lima (UTC−5): último bloque + 1 h")
    void instanteDeFinEnLima() {
        var horario = new ReservaConHorario(RESERVA, LocalDate.of(2026, 10, 30), 14, 17);

        assertThat(horario.instanteDeFin()).isEqualTo(Instant.parse("2026-10-30T22:00:00Z"));
    }

    @Test
    @DisplayName("HU-19 R8: con horaFin = 24 el fin es la medianoche de Lima del día siguiente")
    void instanteDeFinConHoraFin24() {
        var horario = new ReservaConHorario(RESERVA, LocalDate.of(2026, 10, 31), 22, 24);

        assertThat(horario.instanteDeFin()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }
}
