package pe.edu.ulima.espacioya.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PagoTest {

    private static Pago en(EstadoPago estado) {
        return Pago.de(7L, "sim-7-APROBADO-1", new BigDecimal("161.00"), estado, "4242",
                Instant.parse("2026-10-28T15:00:00Z"));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPago.class, names = {"PAGADO", "REEMBOLSO_FALLIDO"})
    @DisplayName("HU-16 R11: un pago PAGADO o REEMBOLSO_FALLIDO pasa a REEMBOLSADO")
    void pasaAReembolsado(EstadoPago estado) {
        var pago = en(estado);

        pago.marcarReembolsado();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPago.class, names = {"PAGADO", "REEMBOLSO_FALLIDO"})
    @DisplayName("HU-16 R17: un pago PAGADO o REEMBOLSO_FALLIDO pasa a REEMBOLSO_FALLIDO")
    void pasaAReembolsoFallido(EstadoPago estado) {
        var pago = en(estado);

        pago.marcarReembolsoFallido();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_FALLIDO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPago.class, names = {"RECHAZADO", "PAGADO_TARDIO", "MONTO_DISTINTO", "REEMBOLSADO"})
    @DisplayName("HU-16 R11, R17: desde RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO o REEMBOLSADO lanza y no cambia")
    void otrosEstadosNoSeReembolsan(EstadoPago estado) {
        var pago = en(estado);

        assertThatThrownBy(pago::marcarReembolsado).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(pago::marcarReembolsoFallido).isInstanceOf(IllegalStateException.class);
        assertThat(pago.getEstado()).isEqualTo(estado);
    }
}
