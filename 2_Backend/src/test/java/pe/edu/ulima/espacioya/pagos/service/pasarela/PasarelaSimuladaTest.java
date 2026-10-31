package pe.edu.ulima.espacioya.pagos.service.pasarela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;

class PasarelaSimuladaTest {

    private static final String SECRETO = "secreto-solo-pruebas";
    private static final String ID = "sim-7-APROBADO-1";
    private static final BigDecimal MONTO = new BigDecimal("241.50");

    private PasarelaSimulada pasarela;

    @BeforeEach
    void preparar() {
        pasarela = new PasarelaSimulada(SECRETO);
    }

    private void checkoutDeLaReservaSiete() {
        pasarela.crearCheckout(new SolicitudCheckout(7L, MONTO, Instant.parse("2026-10-28T15:14:00Z"),
                "http://localhost:5173/reservas/7"));
    }

    private static NotificacionRecibida notificacion(String firma) {
        return new NotificacionRecibida(Map.of("type", "payment", "data.id", ID), Map.of("x-signature", firma));
    }

    @Test
    @DisplayName("HU-13 R56, R59: crear el checkout lleva a la pantalla de pago simulado")
    void creaUnCheckoutSimulado() {
        var creado = pasarela.crearCheckout(new SolicitudCheckout(7L, MONTO, Instant.parse("2026-10-28T15:14:00Z"),
                "http://localhost:5173/reservas/7"));

        assertThat(creado.idCheckout()).isEqualTo("sim-checkout-7");
        assertThat(creado.urlPago()).isEqualTo("http://localhost:5173/reservas/7/pago-simulado");
    }

    @Test
    @DisplayName("HU-13 R62: lo que arma notificacionDe es una notificación de pago con el id simulado")
    void armaLaNotificacion() {
        var notificacion = pasarela.notificacionDe(7L, MONTO, ResultadoPasarela.APROBADO, 1);

        assertThat(notificacion.parametros())
                .containsOnly(entry("type", "payment"), entry("data.id", "sim-7-APROBADO-1"));
        assertThat(pasarela.idNotificado(notificacion)).contains("sim-7-APROBADO-1");
    }

    @Test
    @DisplayName("HU-13 R72: en una instancia nueva, sin checkout en memoria, lo de notificacionDe pasa y se consulta")
    void funcionaTrasReiniciar() {
        var nueva = new PasarelaSimulada(SECRETO);

        var notificacion = nueva.notificacionDe(9L, MONTO, ResultadoPasarela.RECHAZADO, 2);

        assertThat(nueva.consultarPago(nueva.idNotificado(notificacion).orElseThrow())).isEqualTo(new PagoConsultado(
                "sim-9-RECHAZADO-2", ResultadoPasarela.RECHAZADO, MONTO, "PEN", "4242", "9"));
    }

    @Test
    @DisplayName("HU-13 R84: sin secreto configurado, la notificación interna pasa igual")
    void sinSecretoLaInternaPasa() {
        var sinSecreto = new PasarelaSimulada("");

        var notificacion = sinSecreto.notificacionDe(7L, MONTO, ResultadoPasarela.APROBADO, 1);

        assertThat(sinSecreto.idNotificado(notificacion)).contains("sim-7-APROBADO-1");
    }

    @Test
    @DisplayName("HU-13 R85: cada instancia tiene su token, distinto del secreto, y el de una no sirve en otra")
    void cadaInstanciaTieneSuToken() {
        var otra = new PasarelaSimulada(SECRETO);

        var notificacion = pasarela.notificacionDe(7L, MONTO, ResultadoPasarela.APROBADO, 1);
        var deLaOtra = otra.notificacionDe(7L, MONTO, ResultadoPasarela.APROBADO, 1);

        assertThat(notificacion.encabezados().get("x-signature"))
                .isNotEqualTo(deLaOtra.encabezados().get("x-signature"))
                .isNotEqualTo(SECRETO)
                .hasSize(64);
        assertThatThrownBy(() -> otra.idNotificado(notificacion)).isInstanceOf(FirmaInvalidaException.class);
    }

    @Test
    @DisplayName("HU-13 R56: acepta una notificación con x-signature igual al secreto, sin importar las mayúsculas")
    void aceptaElSecreto() {
        var notificacion = new NotificacionRecibida(Map.of("type", "payment", "data.id", ID),
                Map.of("X-Signature", SECRETO));

        assertThat(pasarela.idNotificado(notificacion)).contains(ID);
    }

    @Test
    @DisplayName("HU-13 R23: otro formato no es una notificación de pago y no exige firma")
    void otroFormato() {
        var notificacion = new NotificacionRecibida(Map.of("topic", "merchant_order"), Map.of());

        assertThat(pasarela.idNotificado(notificacion)).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R24: rechaza otra firma, una notificación sin firma y una sin id")
    void rechazaLoQueNoTraeElSecreto() {
        var sinFirma = new NotificacionRecibida(Map.of("type", "payment", "data.id", ID), Map.of());
        var sinId = new NotificacionRecibida(Map.of("type", "payment"), Map.of("x-signature", SECRETO));

        assertThatThrownBy(() -> pasarela.idNotificado(notificacion("otra")))
                .isInstanceOf(FirmaInvalidaException.class);
        assertThatThrownBy(() -> pasarela.idNotificado(sinFirma)).isInstanceOf(FirmaInvalidaException.class);
        assertThatThrownBy(() -> pasarela.idNotificado(sinId)).isInstanceOf(FirmaInvalidaException.class);
    }

    @Test
    @DisplayName("HU-13 R24: sin secreto configurado rechaza toda notificación, incluso con la firma vacía")
    void sinSecretoNadaPasa() {
        var sinSecreto = new PasarelaSimulada("");

        assertThatThrownBy(() -> sinSecreto.idNotificado(notificacion(""))).isInstanceOf(FirmaInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APROBADO", "RECHAZADO", "SIN_RESULTADO"})
    @DisplayName("HU-13 R26: el pago consultado sale del id simulado, con el monto recordado del checkout")
    void consultaUnPagoSimulado(String resultado) {
        checkoutDeLaReservaSiete();
        var id = "sim-7-" + resultado + "-2";

        assertThat(pasarela.consultarPago(id)).isEqualTo(new PagoConsultado(id, ResultadoPasarela.valueOf(resultado),
                MONTO, "PEN", "4242", "7"));
    }

    @Test
    @DisplayName("HU-13 R27: un id que no es simulado o una reserva sin checkout fallan como la consulta real")
    void idDesconocido() {
        checkoutDeLaReservaSiete();

        assertThatThrownBy(() -> pasarela.consultarPago("185919482939")).isInstanceOf(ServicioExternoException.class);
        assertThatThrownBy(() -> pasarela.consultarPago("sim-8-APROBADO-1"))
                .isInstanceOf(ServicioExternoException.class);
    }

    @Test
    @DisplayName("HU-13 R41: cerrar el checkout simulado no falla")
    void cerrarNoFalla() {
        assertThatCode(() -> pasarela.cerrarCheckout("sim-checkout-7")).doesNotThrowAnyException();
    }
}
