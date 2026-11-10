package pe.edu.ulima.espacioya.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormatoCorreoTest {

    @Test
    @DisplayName("HU-25 R29: la fecha va en dd/MM/yyyy")
    void fecha() {
        assertThat(FormatoCorreo.fecha(LocalDate.of(2026, 3, 5))).isEqualTo("05/03/2026");
    }

    @Test
    @DisplayName("HU-25 R29: el horario va en HH:00 a HH:00")
    void horario() {
        assertThat(FormatoCorreo.horario(9, 12)).isEqualTo("09:00 a 12:00");
    }

    @Test
    @DisplayName("HU-25 R29: la hora fin 24 se escribe 24:00")
    void horaFinVeinticuatro() {
        assertThat(FormatoCorreo.horario(22, 24)).isEqualTo("22:00 a 24:00");
    }

    @Test
    @DisplayName("HU-25 R36: el monto va en soles con escala 2")
    void monto() {
        assertThat(FormatoCorreo.monto(new BigDecimal("1234.5"))).isEqualTo("S/ 1234.50");
        assertThat(FormatoCorreo.monto(new BigDecimal("120"))).isEqualTo("S/ 120.00");
    }

    @Test
    @DisplayName("HU-25 R36: el instante va en hora de Lima")
    void instanteEnLima() {
        assertThat(FormatoCorreo.instante(Instant.parse("2026-10-28T15:00:00Z"))).isEqualTo("28/10/2026 10:00");
    }
}
