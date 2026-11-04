package pe.edu.ulima.espacioya.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiquidacionTest {

    // 2026-11-05 00:30 en UTC es todavía el 4 de noviembre en Lima.
    private static final Instant CORTE = Instant.parse("2026-11-05T00:30:00Z");
    private static final Instant AHORA = CORTE.plus(Duration.ofDays(1));
    private static final String NUMERO = "00112233445566778899";

    private static Liquidacion generada(boolean conCuenta) {
        return Liquidacion.generar(7L, List.of(new BigDecimal("241.50"), new BigDecimal("80.50")), conCuenta, true,
                CORTE, 1L);
    }

    private static CuentaBancaria cuenta() {
        return CuentaBancaria.registrar(7L, "Banco Andino", NUMERO, "Canchas SAC", CORTE.minus(Duration.ofDays(30)));
    }

    private static CuentaBancaria retenida() {
        var cuenta = cuenta();
        cuenta.cambiar("Otro Banco", "99887766554433221100", "Canchas SAC", AHORA.minus(Duration.ofHours(1)));
        return cuenta;
    }

    @Test
    @DisplayName("HU-19 R13: bruto = suma de líneas, comisión = 10 % HALF_UP del total y neto = bruto − comisión")
    void calculaLosMontos() {
        var liquidacion = generada(true);

        assertThat(liquidacion.getMontoBruto()).isEqualTo(new BigDecimal("322.00"));
        assertThat(liquidacion.getComision()).isEqualTo(new BigDecimal("32.20"));
        assertThat(liquidacion.getMontoNeto()).isEqualTo(new BigDecimal("289.80"));
        assertThat(liquidacion.getArrendadorId()).isEqualTo(7L);
        assertThat(liquidacion.getGeneradaEn()).isEqualTo(CORTE);
        assertThat(liquidacion.getGeneradaPor()).isEqualTo(1L);
        assertThat(liquidacion.isSimulada()).isTrue();
    }

    @Test
    @DisplayName("HU-19 R13: el redondeo se aplica una sola vez sobre el total (10.05 → 1.01, neto 9.04)")
    void redondeaSobreElTotal() {
        var liquidacion = Liquidacion.generar(7L, List.of(new BigDecimal("5.02"), new BigDecimal("5.03")), true, false,
                CORTE, 1L);

        assertThat(liquidacion.getMontoBruto()).isEqualTo(new BigDecimal("10.05"));
        assertThat(liquidacion.getComision()).isEqualTo(new BigDecimal("1.01"));
        assertThat(liquidacion.getMontoNeto()).isEqualTo(new BigDecimal("9.04"));
    }

    @Test
    @DisplayName("HU-19 R19: el periodo es la fecha del corte en America/Lima")
    void periodoEnLima() {
        assertThat(generada(true).getPeriodo()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    @DisplayName("HU-19 R15: con cuenta vigente la liquidación nace GENERADA y por pagar")
    void conCuentaNaceGenerada() {
        var liquidacion = generada(true);

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.GENERADA);
        assertThat(liquidacion.esPorPagar()).isTrue();
        assertThat(liquidacion.getNumeroOperacion()).isNull();
        assertThat(liquidacion.getPagadaEn()).isNull();
    }

    @Test
    @DisplayName("HU-19 R16: sin cuenta vigente la liquidación nace PENDIENTE_CUENTA y por pagar")
    void sinCuentaNacePendienteDeCuenta() {
        var liquidacion = generada(false);

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PENDIENTE_CUENTA);
        assertThat(liquidacion.esPorPagar()).isTrue();
    }

    @Test
    @DisplayName("HU-19 R7: una liquidación sin líneas no se puede generar")
    void sinLineasNoSeGenera() {
        assertThatThrownBy(() -> Liquidacion.generar(7L, List.of(), true, false, CORTE, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HU-19 R25: con cuenta vigente no retenida es pagable y al pagar guarda operación, fecha, admin, "
            + "banco y la cuenta enmascarada")
    void marcarPagadaGuardaElDestinoEnmascarado() {
        var liquidacion = generada(true);
        var cuenta = cuenta();

        assertThat(liquidacion.motivoNoPagable(Optional.of(cuenta), AHORA)).isEmpty();
        liquidacion.marcarPagada("OP-00123456", cuenta, 3L, AHORA);

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PAGADA);
        assertThat(liquidacion.esPorPagar()).isFalse();
        assertThat(liquidacion.getNumeroOperacion()).isEqualTo("OP-00123456");
        assertThat(liquidacion.getPagadaEn()).isEqualTo(AHORA);
        assertThat(liquidacion.getPagadaPor()).isEqualTo(3L);
        assertThat(liquidacion.getBancoDestino()).isEqualTo("Banco Andino");
        assertThat(liquidacion.getCuentaDestino()).isEqualTo("****8899").doesNotContain(NUMERO);
        assertThat(liquidacion.getMontoNeto()).isEqualTo(new BigDecimal("289.80"));
    }

    @Test
    @DisplayName("HU-19 R26: una PENDIENTE_CUENTA pasa directo a PAGADA si ahora hay cuenta no retenida")
    void pendienteDeCuentaPasaAPagada() {
        var liquidacion = generada(false);

        liquidacion.marcarPagada("00123456", cuenta(), 3L, AHORA);

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PAGADA);
    }

    @Test
    @DisplayName("HU-19 R27: sin cuenta el motivo es SIN_CUENTA y marcarla pagada lanza IllegalStateException")
    void sinCuenta() {
        var liquidacion = generada(false);

        assertThat(liquidacion.motivoNoPagable(Optional.empty(), AHORA)).contains(MotivoNoPagable.SIN_CUENTA);
        assertThatThrownBy(() -> liquidacion.marcarPagada("00123456", null, 3L, AHORA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PENDIENTE_CUENTA);
        assertThat(liquidacion.getNumeroOperacion()).isNull();
    }

    @Test
    @DisplayName("HU-19 R28: con la cuenta retenida el motivo es CUENTA_RETENIDA hasta retener_hasta y no se paga")
    void cuentaRetenida() {
        var liquidacion = generada(true);
        var cuenta = retenida();

        assertThat(liquidacion.motivoNoPagable(Optional.of(cuenta), AHORA)).contains(MotivoNoPagable.CUENTA_RETENIDA);
        assertThatThrownBy(() -> liquidacion.marcarPagada("00123456", cuenta, 3L, AHORA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.GENERADA);
        assertThat(liquidacion.motivoNoPagable(Optional.of(cuenta), cuenta.getRetenerHasta())).isEmpty();
    }

    @Test
    @DisplayName("HU-19 R29: una PAGADA da YA_PAGADA antes que cualquier otro motivo y no cambia al volver a pagarla")
    void yaPagada() {
        var liquidacion = generada(true);
        liquidacion.marcarPagada("PRIMERA", cuenta(), 3L, AHORA);

        assertThat(liquidacion.motivoNoPagable(Optional.empty(), AHORA)).contains(MotivoNoPagable.YA_PAGADA);
        assertThat(liquidacion.motivoNoPagable(Optional.of(retenida()), AHORA)).contains(MotivoNoPagable.YA_PAGADA);
        assertThatThrownBy(() -> liquidacion.marcarPagada("SEGUNDA", cuenta(), 4L, AHORA.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(liquidacion.getNumeroOperacion()).isEqualTo("PRIMERA");
        assertThat(liquidacion.getPagadaPor()).isEqualTo(3L);
        assertThat(liquidacion.getPagadaEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-19 R23: la línea guarda reserva, pago, monto y si es simulada")
    void lineaDeDetalle() {
        var linea = LiquidacionDetalle.de(5L, 11L, 21L, new BigDecimal("241.50"), true);

        assertThat(linea.getLiquidacionId()).isEqualTo(5L);
        assertThat(linea.getReservaId()).isEqualTo(11L);
        assertThat(linea.getPagoId()).isEqualTo(21L);
        assertThat(linea.getMonto()).isEqualTo(new BigDecimal("241.50"));
        assertThat(linea.isSimulado()).isTrue();
    }
}
