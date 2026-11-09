package pe.edu.ulima.espacioya.administracion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.pagos.domain.ReglasLiquidacion;

class ReglasReclamoTest {

    // Reserva del 2026-11-04 de 14:00 a 16:00 en Lima.
    private static final Instant INICIO = Instant.parse("2026-11-04T19:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-04T21:00:00Z");

    private static Object motivo(boolean confirmada, Instant ahora) {
        return ReglasReclamo.motivoNoReclamable(confirmada, INICIO, FIN, ahora).orElse(null);
    }

    @Test
    @DisplayName("HU-26 R4: en el instante exacto del inicio ya se puede reclamar")
    void enElInicio() {
        assertThat(motivo(true, INICIO)).isNull();
    }

    @Test
    @DisplayName("HU-26 R4: durante la reserva y después de su fin, dentro de las 48 h, se puede reclamar")
    void dentroDeLaVentana() {
        assertThat(motivo(true, INICIO.plusSeconds(60))).isNull();
        assertThat(motivo(true, FIN)).isNull();
        assertThat(motivo(true, FIN.plus(Duration.ofHours(47)))).isNull();
    }

    @Test
    @DisplayName("HU-26 R4: un nanosegundo antes de fin + 48 h todavía se puede reclamar")
    void justoAntesDelBorde() {
        assertThat(motivo(true, FIN.plus(Duration.ofHours(48)).minusNanos(1))).isNull();
    }

    @Test
    @DisplayName("HU-26 R7: en fin + 48 h exactos el plazo venció")
    void enElBorde() {
        assertThat(motivo(true, FIN.plus(Duration.ofHours(48)))).isEqualTo(MotivoNoReclamable.PLAZO_VENCIDO);
        assertThat(motivo(true, FIN.plus(Duration.ofDays(10)))).isEqualTo(MotivoNoReclamable.PLAZO_VENCIDO);
    }

    @Test
    @DisplayName("HU-26 R7: el plazo es la misma espera de la liquidación, sin hueco ni solape con esLiquidable")
    void mismoPlazoQueLaLiquidacion() {
        var borde = FIN.plus(ReglasReclamo.PLAZO);

        assertThat(ReglasReclamo.PLAZO).isEqualTo(ReglasLiquidacion.ESPERA).isEqualTo(Duration.ofHours(48));
        assertThat(ReglasLiquidacion.esLiquidable(FIN, borde)).isTrue();
        assertThat(motivo(true, borde)).isEqualTo(MotivoNoReclamable.PLAZO_VENCIDO);
        assertThat(ReglasLiquidacion.esLiquidable(FIN, borde.minusNanos(1))).isFalse();
        assertThat(motivo(true, borde.minusNanos(1))).isNull();
    }

    @Test
    @DisplayName("HU-26 R6: antes del inicio no es reclamable")
    void antesDelInicio() {
        assertThat(motivo(true, INICIO.minusNanos(1))).isEqualTo(MotivoNoReclamable.NO_RECLAMABLE);
        assertThat(motivo(true, INICIO.minus(Duration.ofDays(3)))).isEqualTo(MotivoNoReclamable.NO_RECLAMABLE);
    }

    @Test
    @DisplayName("HU-26 R5: una reserva no confirmada no es reclamable, aunque esté dentro de la ventana o vencida")
    void noConfirmada() {
        assertThat(motivo(false, FIN)).isEqualTo(MotivoNoReclamable.NO_RECLAMABLE);
        assertThat(motivo(false, FIN.plus(Duration.ofDays(10)))).isEqualTo(MotivoNoReclamable.NO_RECLAMABLE);
    }

    @Test
    @DisplayName("HU-26 R4, R7: con horaFin 24 el fin son las 00:00 del día siguiente en Lima")
    void conHoraFin24() {
        var inicio = LocalDate.of(2026, 11, 4).atTime(23, 0).atZone(ZonaHoraria.LIMA).toInstant();
        var fin = LocalDate.of(2026, 11, 5).atStartOfDay(ZonaHoraria.LIMA).toInstant();

        assertThat(ReglasReclamo.motivoNoReclamable(true, inicio, fin, fin.plus(Duration.ofHours(48)).minusNanos(1)))
                .isEmpty();
        assertThat(ReglasReclamo.motivoNoReclamable(true, inicio, fin, fin.plus(Duration.ofHours(48))))
                .contains(MotivoNoReclamable.PLAZO_VENCIDO);
    }

    @Test
    @DisplayName("HU-26 R27: el límite es de 10 mensajes por parte")
    void limiteDeMensajes() {
        assertThat(ReglasReclamo.MAXIMO_MENSAJES_POR_PARTE).isEqualTo(10);
    }
}
