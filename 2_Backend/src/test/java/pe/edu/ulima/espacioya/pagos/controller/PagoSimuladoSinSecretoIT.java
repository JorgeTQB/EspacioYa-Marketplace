package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/**
 * Como UAT (HU-13 D7 revisada, S16): simulada con el secreto vacío. La ruta pública del webhook rechaza todo pago, y la
 * pantalla es la única entrada.
 */
@SpringBootTest(properties = "espacioya.pagos.secreto-webhook=")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class PagoSimuladoSinSecretoIT extends PagosSoporte {

    @ParameterizedTest
    @ValueSource(strings = {"", "secreto-solo-pruebas", "cualquier-valor"})
    @DisplayName("HU-13 R83: el webhook responde 401 sin cuerpo con cualquier firma, incluida la vacía")
    void elWebhookRechazaTodo(String firma) throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigente();
        pagar(sesion, reserva).andExpect(status().isOk());

        mvc.perform(desde(post("/api/pagos/webhook").queryParam("type", "payment")
                        .queryParam("data.id", "sim-" + reserva + "-APROBADO-1")
                        .header("x-signature", firma), ip))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        assertThat(filas("pago")).isZero();
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R83: sin x-signature también responde 401")
    void sinFirma() throws Exception {
        mvc.perform(desde(post("/api/pagos/webhook").queryParam("type", "payment")
                        .queryParam("data.id", "sim-1-APROBADO-1"), ip))
                .andExpect(status().isUnauthorized());

        assertThat(filas("pago")).isZero();
    }

    @Test
    @DisplayName("HU-13 R84: con el secreto vacío, la pantalla aprueba por el camino real")
    void laPantallaApruebaIgual() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigente();
        pagar(sesion, reserva).andExpect(status().isOk());

        mvc.perform(con(sesion, post("/api/reservas/" + reserva + "/pago/simulacion"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isNoContent());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("PAGADO");
            assertThat(fila.get("id_transaccion_pasarela")).isEqualTo("sim-" + reserva + "-APROBADO-1");
        });
    }
}
