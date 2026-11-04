package pe.edu.ulima.espacioya.pagos.service.pasarela;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** La pasarela se elige solo con {@code espacioya.pagos.pasarela}, sin fábrica (HU-13 DT-9). */
class SeleccionPasarelaTest {

    private static final String LLAVE = "espacioya.pagos.llave-secreta=llave-solo-pruebas";
    private static final String SECRETO = "espacioya.pagos.secreto-webhook=secreto-solo-pruebas";
    private static final String URL = "espacioya.pagos.url-notificacion=https://ejemplo.invalid/api/pagos/webhook";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class, MercadoPagoAdapter.class, PasarelaSimulada.class);

    @Configuration(proxyBeanMethods = false)
    static class Configuracion {

        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
        }

        // Como SesionesCheckout y WebhookPagoService: sin una PasarelaPago, el contexto no arranca.
        @Bean
        UsuarioDeLaPasarela usuarioDeLaPasarela(PasarelaPago pasarela) {
            return new UsuarioDeLaPasarela(pasarela);
        }
    }

    record UsuarioDeLaPasarela(PasarelaPago pasarela) {
    }

    private static void noArranca(AssertableApplicationContext arrancado) {
        assertThat(arrancado).hasFailed();
    }

    @Test
    @DisplayName("HU-13 R55: con mercadopago y sus tres valores solo existe el adaptador de Mercado Pago")
    void mercadoPago() {
        contexto.withPropertyValues("espacioya.pagos.pasarela=mercadopago", LLAVE, SECRETO, URL).run(arrancado -> {
            assertThat(arrancado).hasSingleBean(PasarelaPago.class);
            assertThat(arrancado.getBean(PasarelaPago.class)).isInstanceOf(MercadoPagoAdapter.class);
        });
    }

    @Test
    @DisplayName("HU-13 R56: con simulada solo existe la pasarela simulada, y no pide llaves")
    void simulada() {
        contexto.withPropertyValues("espacioya.pagos.pasarela=simulada").run(arrancado -> {
            assertThat(arrancado).hasSingleBean(PasarelaPago.class);
            assertThat(arrancado.getBean(PasarelaPago.class)).isInstanceOf(PasarelaSimulada.class);
        });
    }

    @Test
    @DisplayName("HU-13 R57: sin la propiedad el backend no arranca")
    void sinPropiedad() {
        contexto.run(SeleccionPasarelaTest::noArranca);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "stripe", "mercado-pago", "simulada2"})
    @DisplayName("HU-13 R57: vacía o con otro valor el backend no arranca")
    void otroValor(String valor) {
        contexto.withPropertyValues("espacioya.pagos.pasarela=" + valor).run(SeleccionPasarelaTest::noArranca);
    }

    @Test
    @DisplayName("HU-13 R58: con mercadopago y sin llave secreta el backend no arranca")
    void sinLlave() {
        contexto.withPropertyValues("espacioya.pagos.pasarela=mercadopago", SECRETO, URL)
                .run(SeleccionPasarelaTest::noArranca);
    }

    @Test
    @DisplayName("HU-13 R58: con mercadopago y sin secreto del webhook el backend no arranca")
    void sinSecreto() {
        contexto.withPropertyValues("espacioya.pagos.pasarela=mercadopago", LLAVE, URL)
                .run(SeleccionPasarelaTest::noArranca);
    }

    @Test
    @DisplayName("HU-13 R58: con mercadopago y sin URL de notificación el backend no arranca")
    void sinUrl() {
        contexto.withPropertyValues("espacioya.pagos.pasarela=mercadopago", LLAVE, SECRETO)
                .run(SeleccionPasarelaTest::noArranca);
    }

    @Test
    @DisplayName("HU-13 R58: el error nombra la variable que falta, nunca un valor")
    void elErrorNombraLaVariable() {
        contexto.withPropertyValues("espacioya.pagos.pasarela=mercadopago", SECRETO, URL).run(arrancado ->
                assertThat(arrancado.getStartupFailure()).rootCause()
                        .hasMessageContaining("PAGOS_LLAVE_SECRETA")
                        .hasMessageNotContaining("secreto-solo-pruebas"));
    }
}
