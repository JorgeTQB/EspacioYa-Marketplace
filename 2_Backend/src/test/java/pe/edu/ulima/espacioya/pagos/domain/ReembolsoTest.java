package pe.edu.ulima.espacioya.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReembolsoTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final BigDecimal MONTO = new BigDecimal("161.00");

    private static Reembolso nuevo() {
        var reembolso = Reembolso.porCancelacion(31L, MONTO, AHORA);
        ReflectionTestUtils.setField(reembolso, "id", 4L);
        return reembolso;
    }

    private static Reembolso fallido() {
        var reembolso = nuevo();
        reembolso.iniciarIntento(AHORA.plus(Duration.ofMinutes(3)));
        reembolso.marcarFallido();
        return reembolso;
    }

    @Test
    @DisplayName("HU-16 R1: un reembolso por cancelación nace PENDIENTE, CANCELACION, sin intentos y con la hora dada")
    void valoresIniciales() {
        var reembolso = nuevo();

        assertThat(reembolso.getPagoId()).isEqualTo(31L);
        assertThat(reembolso.getMonto()).isEqualByComparingTo("161.00");
        assertThat(reembolso.getOrigen()).isEqualTo(OrigenReembolso.CANCELACION);
        assertThat(reembolso.getEstado()).isEqualTo(EstadoReembolso.PENDIENTE);
        assertThat(reembolso.getIntentos()).isZero();
        assertThat(reembolso.getCreadoEn()).isEqualTo(AHORA);
        assertThat(reembolso.getUltimoIntentoEn()).isNull();
        assertThat(reembolso.getIdReembolsoPasarela()).isNull();
    }

    @Test
    @DisplayName("HU-16 R21: un REEMBOLSADO no se puede reintentar nunca")
    void reembolsadoNoSeReintenta() {
        var reembolso = nuevo();
        reembolso.iniciarIntento(AHORA.plus(Duration.ofMinutes(2)));
        reembolso.marcarReembolsado("sim-reembolso-1");

        assertThat(reembolso.motivoNoReintentable(AHORA.plus(Duration.ofDays(30))))
                .contains(MotivoNoReintentable.YA_REEMBOLSADO);
        assertThatThrownBy(() -> reembolso.iniciarIntento(AHORA.plus(Duration.ofDays(30))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-16 R22: un PENDIENTE recién creado o a 1 min 59 s está en proceso; a 2 min exactos, sin respuesta")
    void margenSinRespuesta() {
        var reembolso = nuevo();

        assertThat(reembolso.motivoNoReintentable(AHORA)).contains(MotivoNoReintentable.EN_PROCESO);
        assertThat(reembolso.motivoNoReintentable(AHORA.plusSeconds(119))).contains(MotivoNoReintentable.EN_PROCESO);
        assertThat(reembolso.motivoNoReintentable(AHORA.plusSeconds(120))).isEmpty();
    }

    @Test
    @DisplayName("HU-16 R5, R22: el primer intento se admite enseguida aunque el recién creado esté en proceso; "
            + "otro intento dentro del margen, no")
    void primerIntentoSeAdmite() {
        var reembolso = nuevo();

        assertThat(reembolso.admiteIntento(AHORA)).isTrue();
        reembolso.iniciarIntento(AHORA);

        assertThat(reembolso.getIntentos()).isEqualTo(1);
        assertThat(reembolso.getUltimoIntentoEn()).isEqualTo(AHORA);
        assertThat(reembolso.admiteIntento(AHORA.plusSeconds(119))).isFalse();
        assertThatThrownBy(() -> reembolso.iniciarIntento(AHORA.plusSeconds(119)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reembolso.admiteIntento(AHORA.plusSeconds(120))).isTrue();
    }

    @Test
    @DisplayName("HU-16 R22: el margen se cuenta desde el último intento, no desde la creación")
    void margenDesdeElUltimoIntento() {
        var reembolso = nuevo();
        var intento = AHORA.plus(Duration.ofMinutes(10));
        reembolso.iniciarIntento(intento);

        assertThat(reembolso.motivoNoReintentable(intento.plusSeconds(119))).contains(MotivoNoReintentable.EN_PROCESO);
        assertThat(reembolso.motivoNoReintentable(intento.plusSeconds(120))).isEmpty();
    }

    @Test
    @DisplayName("HU-16 R22: un FALLIDO se puede reintentar enseguida")
    void fallidoSeReintenta() {
        var reembolso = fallido();

        assertThat(reembolso.motivoNoReintentable(reembolso.getUltimoIntentoEn())).isEmpty();
    }

    @Test
    @DisplayName("HU-16 R18: iniciarIntento suma 1, fija la hora y deja PENDIENTE; con motivo lanza sin cambiar nada")
    void iniciarIntento() {
        var reembolso = fallido();
        var otra = AHORA.plus(Duration.ofHours(1));

        reembolso.iniciarIntento(otra);

        assertThat(reembolso.getIntentos()).isEqualTo(2);
        assertThat(reembolso.getUltimoIntentoEn()).isEqualTo(otra);
        assertThat(reembolso.getEstado()).isEqualTo(EstadoReembolso.PENDIENTE);
        assertThatThrownBy(() -> reembolso.iniciarIntento(otra.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reembolso.getIntentos()).isEqualTo(2);
        assertThat(reembolso.getUltimoIntentoEn()).isEqualTo(otra);
    }

    @Test
    @DisplayName("HU-16 R10: marcarReembolsado guarda el id de la pasarela; R17: marcarFallido deja FALLIDO sin id")
    void marcarResultado() {
        var exito = nuevo();
        exito.iniciarIntento(AHORA.plus(Duration.ofMinutes(2)));
        exito.marcarReembolsado("sim-reembolso-espacioya-reembolso-4");

        assertThat(exito.getEstado()).isEqualTo(EstadoReembolso.REEMBOLSADO);
        assertThat(exito.getIdReembolsoPasarela()).isEqualTo("sim-reembolso-espacioya-reembolso-4");
        var fallo = fallido();
        assertThat(fallo.getEstado()).isEqualTo(EstadoReembolso.FALLIDO);
        assertThat(fallo.getIdReembolsoPasarela()).isNull();
    }

    @Test
    @DisplayName("HU-16 R10, R17: marcar un resultado fuera de PENDIENTE lanza IllegalStateException")
    void marcarDesdeOtroEstado() {
        var fallo = fallido();
        var exito = nuevo();
        exito.iniciarIntento(AHORA.plus(Duration.ofMinutes(2)));
        exito.marcarReembolsado("r-1");

        assertThatThrownBy(fallo::marcarFallido).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fallo.marcarReembolsado("r-2")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(exito::marcarFallido).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exito.marcarReembolsado("r-3")).isInstanceOf(IllegalStateException.class);
        assertThat(exito.getIdReembolsoPasarela()).isEqualTo("r-1");
    }

    @Test
    @DisplayName("HU-16 R24: la clave de idempotencia es espacioya-reembolso-<id> y no cambia entre intentos")
    void claveIdempotencia() {
        var reembolso = nuevo();
        var antes = reembolso.claveIdempotencia();
        reembolso.iniciarIntento(AHORA.plus(Duration.ofMinutes(2)));
        reembolso.marcarFallido();
        reembolso.iniciarIntento(AHORA.plus(Duration.ofMinutes(3)));

        assertThat(antes).isEqualTo("espacioya-reembolso-4");
        assertThat(reembolso.claveIdempotencia()).isEqualTo(antes);
    }
}
