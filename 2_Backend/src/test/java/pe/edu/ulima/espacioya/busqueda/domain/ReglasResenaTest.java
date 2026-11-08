package pe.edu.ulima.espacioya.busqueda.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglasResenaTest {

    private static final Instant FIN = Instant.parse("2026-10-20T17:00:00Z");
    private static final Instant VENCE = FIN.plus(Duration.ofDays(14));

    @Test
    @DisplayName("HU-24 R5: el instante exacto del fin ya está dentro del plazo")
    void finExacto() {
        assertThat(ReglasResena.motivoNoResenable(true, FIN, FIN)).isEmpty();
    }

    @Test
    @DisplayName("HU-24 R5: un instante antes de fin + 14 días sigue dentro del plazo")
    void unInstanteAntesDeVencer() {
        assertThat(ReglasResena.motivoNoResenable(true, FIN, VENCE.minusNanos(1))).isEmpty();
    }

    @Test
    @DisplayName("HU-24 R7: un instante antes del fin, la reserva confirmada aún no se puede reseñar")
    void unInstanteAntesDelFin() {
        assertThat(ReglasResena.motivoNoResenable(true, FIN, FIN.minusNanos(1)))
                .contains(MotivoNoResenable.NO_USADA);
    }

    @Test
    @DisplayName("HU-24 R8: en fin + 14 días exactos el plazo ya venció")
    void venceExacto() {
        assertThat(ReglasResena.motivoNoResenable(true, FIN, VENCE)).contains(MotivoNoResenable.PLAZO_VENCIDO);
    }

    @Test
    @DisplayName("HU-24 R6: una reserva no confirmada no se reseña, ni dentro del plazo ni después")
    void noConfirmada() {
        assertThat(ReglasResena.motivoNoResenable(false, FIN, FIN.plusSeconds(60)))
                .contains(MotivoNoResenable.NO_USADA);
        assertThat(ReglasResena.motivoNoResenable(false, FIN, VENCE.plusSeconds(60)))
                .contains(MotivoNoResenable.NO_USADA);
    }
}
