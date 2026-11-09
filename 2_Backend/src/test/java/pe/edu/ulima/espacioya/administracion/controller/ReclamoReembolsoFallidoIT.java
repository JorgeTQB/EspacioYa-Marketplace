package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/**
 * HU-26 R45 con la pasarela simulada fallando el primer intento de cada reembolso ({@code fallos-reembolso=1}): el
 * reclamo queda resuelto igual y el reembolso fallido aparece en el panel de HU-16.
 */
@SpringBootTest(properties = "espacioya.pagos.simulada.fallos-reembolso=1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReclamoReembolsoFallidoIT extends ReclamosSoporte {

    @Test
    @DisplayName("HU-26 R45, R59: si la pasarela falla, el reembolso queda FALLIDO, el pago REEMBOLSO_FALLIDO, el "
            + "reclamo resuelto y visible en reembolsos por atender")
    void pasarelaFalla() throws Exception {
        crearAdmin();
        var sesion = token(CORREO_ADMIN);
        var reserva = reclamableConPago();
        var reclamo = sembrarReclamo(reserva);

        mvc.perform(comoAdmin(sesion, post(URI.create(ADMIN_RECLAMOS + "/" + reclamo + "/resolucion")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aFavorDe\":\"USUARIO\",\"reembolso\":\"TOTAL\",\"resolucion\":\"Procede\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTO_USUARIO"));

        var reembolso = jdbc.queryForMap("SELECT * FROM reembolso");
        assertThat(reembolso.get("origen")).isEqualTo("RECLAMO");
        assertThat(reembolso.get("estado")).isEqualTo("FALLIDO");
        assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE reserva_id = ?", String.class, reserva))
                .isEqualTo("REEMBOLSO_FALLIDO");
        assertThat(reclamoDe(reserva).get("estado")).isEqualTo("RESUELTO_USUARIO");
        assertThat(auditorias("REEMBOLSO_FALLIDO")).hasSize(1);
        assertThat(correos.enviados()).hasSize(2);
        mvc.perform(comoAdmin(sesion, get("/api/admin/reembolsos"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(numero(reembolso.get("id"))));
    }
}
