package pe.edu.ulima.espacioya.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglasLiquidacionTest {

    private static final Instant FIN = Instant.parse("2026-11-04T22:00:00Z");

    @Test
    @DisplayName("HU-19 R8: una reserva es liquidable desde su fin + 48 h exacto, no un segundo antes")
    void bordeDe48Horas() {
        assertThat(ReglasLiquidacion.ESPERA).isEqualTo(Duration.ofHours(48));
        var borde = FIN.plus(Duration.ofHours(48));

        assertThat(ReglasLiquidacion.esLiquidable(FIN, borde.minusSeconds(1))).isFalse();
        assertThat(ReglasLiquidacion.esLiquidable(FIN, borde)).isTrue();
        assertThat(ReglasLiquidacion.esLiquidable(FIN, borde.plusSeconds(1))).isTrue();
        assertThat(ReglasLiquidacion.esLiquidable(FIN, FIN)).isFalse();
    }

    @Test
    @DisplayName("HU-19 R13: la comisión es el 10 % del bruto con escala 2 y HALF_UP")
    void comisionDel10PorCiento() {
        assertThat(ReglasLiquidacion.TASA_COMISION).isEqualByComparingTo("0.10");
        assertThat(ReglasLiquidacion.comision(new BigDecimal("241.50"))).isEqualTo(new BigDecimal("24.15"));
        assertThat(ReglasLiquidacion.comision(new BigDecimal("10.05"))).isEqualTo(new BigDecimal("1.01"));
        assertThat(ReglasLiquidacion.comision(new BigDecimal("10.04"))).isEqualTo(new BigDecimal("1.00"));
        assertThat(ReglasLiquidacion.comision(new BigDecimal("0.05"))).isEqualTo(new BigDecimal("0.01"));
        assertThat(ReglasLiquidacion.comision(new BigDecimal("724.50"))).isEqualTo(new BigDecimal("72.45"));
    }

    @Test
    @DisplayName("HU-19 R20, R21: un pago es simulado si su id de transacción empieza por sim-")
    void pagoSimulado() {
        assertThat(ReglasPago.PREFIJO_SIMULADO).isEqualTo("sim-");
        assertThat(ReglasLiquidacion.esSimulado("sim-12-APROBADO-1")).isTrue();
        assertThat(ReglasLiquidacion.esSimulado("123456789")).isFalse();
        assertThat(ReglasLiquidacion.esSimulado("SIM-12-APROBADO-1")).isFalse();
        assertThat(ReglasLiquidacion.esSimulado("xsim-1")).isFalse();
        assertThat(ReglasLiquidacion.esSimulado(null)).isFalse();
    }
}
