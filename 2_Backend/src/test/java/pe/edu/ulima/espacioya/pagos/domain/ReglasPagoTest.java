package pe.edu.ulima.espacioya.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReglasPagoTest {

    private static final Instant EXPIRA = Instant.parse("2026-10-28T15:15:00Z");
    private static final Duration MARGEN = Duration.ofMinutes(1);
    private static final Instant ANTES = EXPIRA.minusSeconds(61);
    private static final BigDecimal MONTO = new BigDecimal("241.50");

    private static MotivoNoDisponible motivo(boolean vigente, boolean visible, long rechazos, Instant ahora) {
        return ReglasPago.motivoNoDisponible(vigente, EXPIRA, visible, rechazos, ahora, MARGEN);
    }

    @Test
    @DisplayName("HU-13 R1: una reserva vigente, fuera del último tramo, visible y con 2 rechazos está disponible")
    void disponible() {
        assertThat(motivo(true, true, 2, ANTES)).isNull();
    }

    @Test
    @DisplayName("HU-13 R10: una reserva no vigente no se puede pagar")
    void noVigente() {
        assertThat(motivo(false, true, 0, ANTES)).isEqualTo(MotivoNoDisponible.NO_VIGENTE);
    }

    @Test
    @DisplayName("HU-13 R11: a expira_en − 61 s se puede pagar; a expira_en − 60 s ya es el último tramo")
    void ultimoTramo() {
        assertThat(motivo(true, true, 0, EXPIRA.minusSeconds(61))).isNull();
        assertThat(motivo(true, true, 0, EXPIRA.minusSeconds(60))).isEqualTo(MotivoNoDisponible.ULTIMO_TRAMO);
    }

    @Test
    @DisplayName("HU-13 R12: un espacio no visible no se puede pagar")
    void espacioNoVisible() {
        assertThat(motivo(true, false, 0, ANTES)).isEqualTo(MotivoNoDisponible.ESPACIO_NO_VISIBLE);
    }

    @Test
    @DisplayName("HU-13 R13: con 2 rechazos se puede; con 3 se agotaron los intentos")
    void rechazos() {
        assertThat(ReglasPago.intentosAgotados(2)).isFalse();
        assertThat(ReglasPago.intentosAgotados(3)).isTrue();
        assertThat(motivo(true, true, 3, ANTES)).isEqualTo(MotivoNoDisponible.INTENTOS_AGOTADOS);
    }

    @Test
    @DisplayName("HU-13 R13: un rechazo de hace 30 min aún cuenta y uno de hace 31 min ya no")
    void ventanaDeRechazos() {
        var ahora = Instant.parse("2026-10-28T15:30:00Z");
        var desde = ReglasPago.inicioVentanaRechazos(ahora);

        assertThat(ahora.minus(Duration.ofMinutes(30))).isAfterOrEqualTo(desde);
        assertThat(ahora.minus(Duration.ofMinutes(31))).isBefore(desde);
    }

    @Test
    @DisplayName("HU-13 R18: el orden es vigencia, último tramo, espacio y rechazos")
    void ordenDeLasComprobaciones() {
        var enTramo = EXPIRA.minusSeconds(10);

        assertThat(motivo(false, false, 3, enTramo)).isEqualTo(MotivoNoDisponible.NO_VIGENTE);
        assertThat(motivo(true, false, 3, enTramo)).isEqualTo(MotivoNoDisponible.ULTIMO_TRAMO);
        assertThat(motivo(true, false, 3, ANTES)).isEqualTo(MotivoNoDisponible.ESPACIO_NO_VISIBLE);
        assertThat(motivo(true, true, 3, ANTES)).isEqualTo(MotivoNoDisponible.INTENTOS_AGOTADOS);
    }

    @Test
    @DisplayName("HU-13 R30: aprobado, coincidente y vigente es PAGADO; 241.5 es igual a 241.50")
    void pagado() {
        assertThat(ReglasPago.estadoDeAprobado(new BigDecimal("241.5"), "PEN", MONTO, true))
                .isEqualTo(EstadoPago.PAGADO);
    }

    @Test
    @DisplayName("HU-13 R33: otro monto u otra moneda es MONTO_DISTINTO, aunque la reserva no sea vigente")
    void montoDistinto() {
        assertThat(ReglasPago.estadoDeAprobado(new BigDecimal("241.51"), "PEN", MONTO, true))
                .isEqualTo(EstadoPago.MONTO_DISTINTO);
        assertThat(ReglasPago.estadoDeAprobado(MONTO, "USD", MONTO, true)).isEqualTo(EstadoPago.MONTO_DISTINTO);
        assertThat(ReglasPago.estadoDeAprobado(MONTO, null, MONTO, true)).isEqualTo(EstadoPago.MONTO_DISTINTO);
        assertThat(ReglasPago.estadoDeAprobado(new BigDecimal("1.00"), "PEN", MONTO, false))
                .isEqualTo(EstadoPago.MONTO_DISTINTO);
    }

    @Test
    @DisplayName("HU-13 R34: aprobado y coincidente con la reserva no vigente es PAGADO_TARDIO")
    void tardio() {
        assertThat(ReglasPago.estadoDeAprobado(MONTO, "PEN", MONTO, false)).isEqualTo(EstadoPago.PAGADO_TARDIO);
    }

    @Test
    @DisplayName("HU-13 R15: un id bien escrito se convierte a número")
    void idBienEscrito() {
        assertThat(ReglasPago.idDeReserva("7")).isEqualTo(7L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "0", "-1", "7 ", "1.5", "99999999999999999999", "9223372036854775808"})
    @DisplayName("HU-13 R15: un id mal escrito, cero o que desborda un long no es un id")
    void idMalEscrito(String texto) {
        assertThat(ReglasPago.idDeReserva(texto)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "9223372036854775807"})
    @DisplayName("HU-16 R32: 1 y un id de 19 dígitos que cabe en un long son ids bien escritos")
    void idBienEscritoDeReembolso(String texto) {
        assertThat(ReglasPago.idBienEscrito(texto)).isEqualTo(Long.parseLong(texto));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "0", "-1", "1.5", "12345678901234567890", "9223372036854775808"})
    @DisplayName("HU-16 R32: vacío, letras, cero, negativo, decimal, 20 dígitos o desborde no son un id")
    void idMalEscritoDeReembolso(String texto) {
        assertThat(ReglasPago.idBienEscrito(texto)).isNull();
    }
}
