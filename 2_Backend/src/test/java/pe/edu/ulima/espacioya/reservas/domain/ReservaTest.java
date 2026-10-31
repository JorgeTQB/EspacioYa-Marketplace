package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.RelojControlable;

class ReservaTest {

    private static final Instant INICIO = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant VENCE = INICIO.plus(Duration.ofMinutes(15));
    private static final Instant UN_SEGUNDO_ANTES = VENCE.minusSeconds(1);

    private final RelojControlable reloj = new RelojControlable(INICIO);

    private Reserva nueva() {
        return Reserva.pendiente(7L, 40L, new BigDecimal("80.50"), 3, reloj.instant());
    }

    private Reserva conEstado(EstadoReserva estado) {
        var reserva = nueva();
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }

    @Test
    @DisplayName("HU-12 R2: nace PENDIENTE_PAGO, del usuario y el espacio dados, y expira a los 15 minutos")
    void naceConLaRetencionDeQuinceMinutos() {
        var reserva = nueva();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(reserva.getUsuarioId()).isEqualTo(7L);
        assertThat(reserva.getEspacioId()).isEqualTo(40L);
        assertThat(reserva.getCreadaEn()).isEqualTo(INICIO);
        assertThat(reserva.getExpiraEn()).isEqualTo(VENCE);
    }

    @Test
    @DisplayName("HU-12 R3: copia el precio por hora y el monto es precio × bloques, sin redondeo")
    void calculaElMontoSinRedondeo() {
        var reserva = nueva();

        assertThat(reserva.getPrecioHoraAplicado()).isEqualByComparingTo("80.50");
        assertThat(reserva.getMontoTotal()).isEqualTo(new BigDecimal("241.50"));
    }

    @Test
    @DisplayName("HU-12 R3: el mayor monto posible, 24 horas a S/ 100 000.00, no pierde precisión")
    void montoMaximo() {
        var reserva = Reserva.pendiente(7L, 40L, new BigDecimal("100000.00"), 24, INICIO);

        assertThat(reserva.getMontoTotal()).isEqualTo(new BigDecimal("2400000.00"));
    }

    @Test
    @DisplayName("HU-12 R9: es vigente hasta un segundo antes de expira_en y vencida desde expira_en")
    void limiteDeVencimiento() {
        var reserva = nueva();

        assertThat(reserva.esVigente(UN_SEGUNDO_ANTES)).isTrue();
        assertThat(reserva.estaVencida(UN_SEGUNDO_ANTES)).isFalse();
        assertThat(reserva.esVigente(VENCE)).isFalse();
        assertThat(reserva.estaVencida(VENCE)).isTrue();
    }

    @Test
    @DisplayName("HU-12 R9: expirar una vencida la deja EXPIRADA")
    void expiraUnaVencida() {
        var reserva = nueva();

        reserva.expirar(VENCE);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        assertThat(reserva.estaVencida(VENCE)).isFalse();
    }

    @Test
    @DisplayName("HU-12 R11, R16: expirar una vigente lanza y no cambia el estado")
    void noExpiraUnaVigente() {
        var reserva = nueva();

        assertThatThrownBy(() -> reserva.expirar(UN_SEGUNDO_ANTES)).isInstanceOf(IllegalStateException.class);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    @DisplayName("HU-12 R11, R16: una CONFIRMADA o ya EXPIRADA no está vencida y expirarla lanza")
    void noExpiraOtrosEstados() {
        var confirmada = conEstado(EstadoReserva.CONFIRMADA);
        var expirada = conEstado(EstadoReserva.EXPIRADA);

        assertThat(confirmada.estaVencida(VENCE)).isFalse();
        assertThat(expirada.estaVencida(VENCE)).isFalse();
        assertThatThrownBy(() -> confirmada.expirar(VENCE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> expirada.expirar(VENCE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-13 R31: confirmar una vigente la deja CONFIRMADA, hasta un segundo antes de expira_en")
    void confirmaUnaVigente() {
        var reserva = nueva();

        reserva.confirmar(UN_SEGUNDO_ANTES);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reserva.estadoEn(VENCE)).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    @DisplayName("HU-13 R31, R34: confirmar una vencida lanza y no cambia el estado")
    void noConfirmaUnaVencida() {
        var reserva = nueva();

        assertThatThrownBy(() -> reserva.confirmar(VENCE)).isInstanceOf(IllegalStateException.class);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    @DisplayName("HU-13 R31, R34: confirmar una CONFIRMADA o una EXPIRADA lanza")
    void noConfirmaOtrosEstados() {
        var confirmada = conEstado(EstadoReserva.CONFIRMADA);
        var expirada = conEstado(EstadoReserva.EXPIRADA);

        assertThatThrownBy(() -> confirmada.confirmar(UN_SEGUNDO_ANTES)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> expirada.confirmar(UN_SEGUNDO_ANTES)).isInstanceOf(IllegalStateException.class);
        assertThat(expirada.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
    }

    @Test
    @DisplayName("HU-12 R39: una vencida se lee EXPIRADA sin cambiar el estado guardado")
    void estadoEnUnaVencida() {
        var reserva = nueva();

        assertThat(reserva.estadoEn(UN_SEGUNDO_ANTES)).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(reserva.estadoEn(VENCE)).isEqualTo(EstadoReserva.EXPIRADA);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    @DisplayName("HU-12 R39: una CONFIRMADA sigue CONFIRMADA después de expira_en")
    void estadoEnUnaConfirmada() {
        assertThat(conEstado(EstadoReserva.CONFIRMADA).estadoEn(VENCE)).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    @DisplayName("HU-12 R40: quedan 900 segundos al crearla, los enteros restantes después y 0 al vencer")
    void segundosRestantes() {
        var reserva = nueva();

        assertThat(reserva.segundosRestantes(INICIO)).isEqualTo(900);
        assertThat(reserva.segundosRestantes(UN_SEGUNDO_ANTES)).isEqualTo(1);
        assertThat(reserva.segundosRestantes(VENCE)).isZero();
        assertThat(reserva.segundosRestantes(VENCE.plusSeconds(60))).isZero();
    }

    @Test
    @DisplayName("HU-12 R40: fuera de PENDIENTE_PAGO quedan 0 segundos aunque expira_en esté en el futuro")
    void segundosRestantesDeOtrosEstados() {
        assertThat(conEstado(EstadoReserva.CONFIRMADA).segundosRestantes(INICIO)).isZero();
        assertThat(conEstado(EstadoReserva.EXPIRADA).segundosRestantes(INICIO)).isZero();
    }
}
