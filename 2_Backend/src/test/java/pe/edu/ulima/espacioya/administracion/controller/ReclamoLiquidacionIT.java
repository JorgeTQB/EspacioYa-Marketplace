package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.administracion.service.ReclamoService;

/**
 * Retención de la liquidación por reclamos (HU-26 CA1, CA2, CA3, RN-12) de punta a punta: reclamo, resolución y
 * {@code POST /api/admin/liquidaciones} contra PostgreSQL real. Las reservas liquidables terminaron hace 3 días.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReclamoLiquidacionIT extends ReclamosSoporte {

    private void generar(String sesion) throws Exception {
        mvc.perform(comoAdmin(sesion, post("/api/admin/liquidaciones"))).andExpect(status().is2xxSuccessful());
    }

    private void resolver(String sesion, long reclamo, String cuerpo) throws Exception {
        mvc.perform(comoAdmin(sesion, post(URI.create(ADMIN_RECLAMOS + "/" + reclamo + "/resolucion")))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
    }

    private List<Long> liquidadas() {
        return jdbc.queryForList("SELECT reserva_id FROM liquidacion_detalle ORDER BY reserva_id", Long.class);
    }

    private long liquidablePagada(int hora) {
        var reserva = confirmada(hoy.minusDays(3), hora);
        pago(reserva, "PAGADO");
        return reserva;
    }

    @Test
    @DisplayName("HU-26 R20, R50: con reclamo abierto la reserva no se liquida; resuelta a favor del arrendador entra "
            + "en la siguiente generación")
    void retieneYLuegoLibera() throws Exception {
        crearAdmin();
        var sesion = token(CORREO_ADMIN);
        var retenida = liquidablePagada(8);
        var libre = liquidablePagada(9);
        var reclamo = sembrarReclamo(retenida);

        generar(sesion);
        assertThat(liquidadas()).containsExactly(libre);

        resolver(sesion, reclamo, "{\"aFavorDe\":\"ARRENDADOR\",\"resolucion\":\"No procede\"}");
        generar(sesion);

        assertThat(liquidadas()).containsExactly(retenida, libre);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM liquidacion", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-26 R48: una reserva con reembolso por reclamo, total o parcial, nunca se liquida")
    void conReembolsoNuncaEntra() throws Exception {
        crearAdmin();
        var sesion = token(CORREO_ADMIN);
        var total = liquidablePagada(8);
        var parcial = liquidablePagada(9);
        resolver(sesion, sembrarReclamo(total), "{\"aFavorDe\":\"USUARIO\",\"reembolso\":\"TOTAL\","
                + "\"resolucion\":\"Procede\"}");
        resolver(sesion, sembrarReclamo(parcial), "{\"aFavorDe\":\"USUARIO\",\"reembolso\":\"PARCIAL\","
                + "\"montoReembolso\":10,\"resolucion\":\"Procede en parte\"}");

        generar(sesion);
        generar(sesion);

        assertThat(liquidadas()).isEmpty();
        assertThat(jdbc.queryForList("SELECT origen FROM reembolso", String.class)).containsOnly("RECLAMO");
    }

    @Test
    @DisplayName("HU-26 R8: abrir un reclamo de una reserva ya liquidada responde plazo vencido aunque esté en la "
            + "ventana")
    void yaLiquidada() throws Exception {
        var admin = crearAdmin();
        var reserva = confirmada(hoy.minusDays(1), 8);
        liquidar(reserva, pago(reserva, "PAGADO"), admin);

        abrir(token(CORREO_USUARIO), reserva).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ReclamoService.MENSAJE_PLAZO_VENCIDO));

        assertThat(filas("reclamo")).isZero();
    }

    @Test
    @DisplayName("HU-26 R21: después de generar, una reserva madura ya liquidada no admite reclamo")
    void despuesDeGenerar() throws Exception {
        crearAdmin();
        var reserva = liquidablePagada(8);
        generar(token(CORREO_ADMIN));

        abrir(token(CORREO_USUARIO), reserva).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ReclamoService.MENSAJE_PLAZO_VENCIDO));
        assertThat(liquidadas()).containsExactly(reserva);
    }
}
