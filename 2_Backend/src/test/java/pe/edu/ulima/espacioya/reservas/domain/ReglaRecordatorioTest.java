package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglaRecordatorioTest {

    // Jueves 29/10/2026 10:00 de Lima.
    private static final Instant INICIO = Instant.parse("2026-10-29T15:00:00Z");

    @Test
    @DisplayName("HU-25 R11, R12: justo 24 h antes del inicio ya corresponde el recordatorio")
    void justoVeinticuatroHorasAntes() {
        assertThat(ReglaRecordatorio.corresponde(INICIO, INICIO.minus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    @DisplayName("HU-25 R15: 24 h y 1 s antes del inicio todavía no corresponde")
    void unSegundoAntesDelPlazo() {
        assertThat(ReglaRecordatorio.corresponde(INICIO, INICIO.minus(Duration.ofHours(24)).minusSeconds(1)))
                .isFalse();
    }

    @Test
    @DisplayName("HU-25 R16: en el inicio exacto ya no corresponde")
    void enElInicio() {
        assertThat(ReglaRecordatorio.corresponde(INICIO, INICIO)).isFalse();
        assertThat(ReglaRecordatorio.corresponde(INICIO, INICIO.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("HU-25 R11 (D2): 2 h antes del inicio corresponde, aunque la reserva se hizo con menos de 24 h")
    void dosHorasAntes() {
        assertThat(ReglaRecordatorio.corresponde(INICIO, INICIO.minus(Duration.ofHours(2)))).isTrue();
        assertThat(ReglaRecordatorio.corresponde(INICIO, INICIO.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("HU-25 R11: la última fecha candidata es la de Lima de ahora + 24 h")
    void ultimaFechaEnLima() {
        // 28/10 18:59 Lima → 29/10 18:59 Lima.
        assertThat(ReglaRecordatorio.ultimaFecha(Instant.parse("2026-10-28T23:59:00Z")))
                .isEqualTo(LocalDate.of(2026, 10, 29));
        // 28/10 19:30 Lima (ya 29/10 en UTC) → 29/10 19:30 Lima, no 30/10.
        assertThat(ReglaRecordatorio.ultimaFecha(Instant.parse("2026-10-29T00:30:00Z")))
                .isEqualTo(LocalDate.of(2026, 10, 29));
    }
}
