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
    private static final Instant EMPIEZA = INICIO.plus(Duration.ofDays(5));

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

    // ── HU-15: cancelación ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R19: una CONFIRMADA que aún no empieza es cancelable")
    void confirmadaFuturaEsCancelable() {
        assertThat(conEstado(EstadoReserva.CONFIRMADA).motivoNoCancelable(EMPIEZA, INICIO)).isEmpty();
        assertThat(conEstado(EstadoReserva.CONFIRMADA).motivoNoCancelable(EMPIEZA, EMPIEZA.minusSeconds(1)))
                .isEmpty();
    }

    @Test
    @DisplayName("HU-15 R8: una CANCELADA da YA_CANCELADA")
    void canceladaDaYaCancelada() {
        assertThat(conEstado(EstadoReserva.CANCELADA).motivoNoCancelable(EMPIEZA, INICIO))
                .contains(MotivoNoCancelable.YA_CANCELADA);
    }

    @Test
    @DisplayName("HU-15 R8: YA_CANCELADA gana aunque la reserva ya haya empezado")
    void yaCanceladaVaPrimero() {
        assertThat(conEstado(EstadoReserva.CANCELADA).motivoNoCancelable(EMPIEZA, EMPIEZA.plusSeconds(1)))
                .contains(MotivoNoCancelable.YA_CANCELADA);
    }

    @Test
    @DisplayName("HU-15 R9: una PENDIENTE_PAGO vigente, una vencida sin procesar y una EXPIRADA dan NO_CONFIRMADA")
    void noConfirmadas() {
        assertThat(nueva().motivoNoCancelable(EMPIEZA, UN_SEGUNDO_ANTES)).contains(MotivoNoCancelable.NO_CONFIRMADA);
        assertThat(nueva().motivoNoCancelable(EMPIEZA, VENCE)).contains(MotivoNoCancelable.NO_CONFIRMADA);
        assertThat(conEstado(EstadoReserva.EXPIRADA).motivoNoCancelable(EMPIEZA, INICIO))
                .contains(MotivoNoCancelable.NO_CONFIRMADA);
    }

    @Test
    @DisplayName("HU-15 R10: una CONFIRMADA cuyo inicio es ahora o ya pasó da YA_INICIADA")
    void confirmadaYaIniciada() {
        assertThat(conEstado(EstadoReserva.CONFIRMADA).motivoNoCancelable(EMPIEZA, EMPIEZA))
                .contains(MotivoNoCancelable.YA_INICIADA);
        assertThat(conEstado(EstadoReserva.CONFIRMADA).motivoNoCancelable(EMPIEZA, EMPIEZA.plusSeconds(3600)))
                .contains(MotivoNoCancelable.YA_INICIADA);
    }

    @Test
    @DisplayName("HU-15 R19, R20: cancelar deja CANCELADA con quién, cuándo y el monto a reembolsar")
    void cancelarGuardaLosTresDatos() {
        var reserva = conEstado(EstadoReserva.CONFIRMADA);

        reserva.cancelar(QuienCancela.ARRENDADOR, EMPIEZA, new BigDecimal("241.50"), INICIO);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reserva.estadoEn(INICIO)).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reserva.getCanceladaPor()).isEqualTo(QuienCancela.ARRENDADOR);
        assertThat(reserva.getCanceladaEn()).isEqualTo(INICIO);
        assertThat(reserva.getMontoReembolso()).isEqualByComparingTo("241.50");
    }

    @Test
    @DisplayName("HU-15 R8, R9, R10: cancelar una no cancelable lanza y no cambia nada")
    void cancelarNoCancelableLanza() {
        var cancelada = conEstado(EstadoReserva.CANCELADA);
        var pendiente = nueva();
        var empezada = conEstado(EstadoReserva.CONFIRMADA);

        assertThatThrownBy(() -> cancelada.cancelar(QuienCancela.USUARIO, EMPIEZA, BigDecimal.ZERO, INICIO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pendiente.cancelar(QuienCancela.USUARIO, EMPIEZA, BigDecimal.ZERO, INICIO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> empezada.cancelar(QuienCancela.USUARIO, EMPIEZA, BigDecimal.ZERO, EMPIEZA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(empezada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(empezada.getCanceladaPor()).isNull();
        assertThat(empezada.getCanceladaEn()).isNull();
        assertThat(empezada.getMontoReembolso()).isNull();
    }

    private Reserva cancelada(QuienCancela quien, String montoReembolso) {
        var reserva = conEstado(EstadoReserva.CONFIRMADA);
        reserva.cancelar(quien, EMPIEZA, montoReembolso == null ? null : new BigDecimal(montoReembolso), INICIO);
        return reserva;
    }

    @Test
    @DisplayName("HU-19 R10: una CONFIRMADA admite liquidación; PENDIENTE_PAGO (vigente o vencida) y EXPIRADA no")
    void admiteLiquidacionSegunElEstado() {
        var pendiente = nueva();

        assertThat(conEstado(EstadoReserva.CONFIRMADA).admiteLiquidacion()).isTrue();
        assertThat(pendiente.admiteLiquidacion()).isFalse();
        assertThat(pendiente.estaVencida(VENCE)).isTrue();
        assertThat(pendiente.admiteLiquidacion()).isFalse();
        assertThat(conEstado(EstadoReserva.EXPIRADA).admiteLiquidacion()).isFalse();
    }

    @Test
    @DisplayName("HU-19 R10: una CANCELADA por el arrendador, con reembolso mayor que 0 o nulo no admite liquidación")
    void canceladaConReembolsoNoAdmiteLiquidacion() {
        assertThat(cancelada(QuienCancela.ARRENDADOR, "241.50").admiteLiquidacion()).isFalse();
        assertThat(cancelada(QuienCancela.ARRENDADOR, "0").admiteLiquidacion()).isFalse();
        assertThat(cancelada(QuienCancela.USUARIO, "241.50").admiteLiquidacion()).isFalse();
        assertThat(cancelada(QuienCancela.USUARIO, "0.01").admiteLiquidacion()).isFalse();
        assertThat(cancelada(QuienCancela.USUARIO, null).admiteLiquidacion()).isFalse();
    }

    @Test
    @DisplayName("HU-19 R11: una CANCELADA por el usuario con monto_reembolso = 0 admite liquidación (D3)")
    void canceladaSinReembolsoAdmiteLiquidacion() {
        assertThat(cancelada(QuienCancela.USUARIO, "0").admiteLiquidacion()).isTrue();
        assertThat(cancelada(QuienCancela.USUARIO, "0.00").admiteLiquidacion()).isTrue();
    }

    @Test
    @DisplayName("HU-25 R14, R17: solo una CONFIRMADA sin recordatorio admite recordatorio")
    void admiteRecordatorio() {
        assertThat(conEstado(EstadoReserva.CONFIRMADA).admiteRecordatorio()).isTrue();
        assertThat(conEstado(EstadoReserva.PENDIENTE_PAGO).admiteRecordatorio()).isFalse();
        assertThat(conEstado(EstadoReserva.EXPIRADA).admiteRecordatorio()).isFalse();
        assertThat(conEstado(EstadoReserva.CANCELADA).admiteRecordatorio()).isFalse();
    }

    @Test
    @DisplayName("HU-25 R13, R14: marcarRecordatorio guarda el ahora y la reserva ya no admite otro")
    void marcaElRecordatorio() {
        var reserva = conEstado(EstadoReserva.CONFIRMADA);

        reserva.marcarRecordatorio(EMPIEZA);

        assertThat(reserva.getRecordatorioEnviadoEn()).isEqualTo(EMPIEZA);
        assertThat(reserva.admiteRecordatorio()).isFalse();
        assertThatThrownBy(() -> reserva.marcarRecordatorio(EMPIEZA)).isInstanceOf(IllegalStateException.class);
        assertThat(reserva.getRecordatorioEnviadoEn()).isEqualTo(EMPIEZA);
    }

    @Test
    @DisplayName("HU-25 R17: no se marca el recordatorio de una reserva pendiente, expirada o cancelada")
    void noMarcaSinConfirmar() {
        for (var estado : new EstadoReserva[] {EstadoReserva.PENDIENTE_PAGO, EstadoReserva.EXPIRADA,
                EstadoReserva.CANCELADA}) {
            var reserva = conEstado(estado);
            assertThatThrownBy(() -> reserva.marcarRecordatorio(EMPIEZA)).isInstanceOf(IllegalStateException.class);
            assertThat(reserva.getRecordatorioEnviadoEn()).isNull();
        }
    }

    @Test
    @DisplayName("HU-25 R14: una reserva nueva no tiene recordatorio")
    void nuevaSinRecordatorio() {
        assertThat(nueva().getRecordatorioEnviadoEn()).isNull();
    }
}
