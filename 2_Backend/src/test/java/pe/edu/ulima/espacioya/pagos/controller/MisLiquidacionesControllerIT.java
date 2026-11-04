package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.pagos.service.MisLiquidacionesService;

/** "Mis liquidaciones" de HU-19 por la API contra PostgreSQL real: CA2, CA3, propiedad, minimización y acceso. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class MisLiquidacionesControllerIT extends LiquidacionesSoporte {

    private LocalDate hace5Dias() {
        return ZonaHoraria.hoy(reloj).minusDays(5);
    }

    /** Una línea de la liquidación con una reserva usada del arrendador principal; devuelve el id de la reserva. */
    private long lineaEn(long liquidacion, int hora) {
        var reserva = usadaSembrada(espacioId, hace5Dias(), hora, transaccionSimulada());
        var pago = jdbc.queryForObject("SELECT id FROM pago WHERE reserva_id = ?", Long.class, reserva);
        jdbc.update("INSERT INTO liquidacion_detalle (liquidacion_id, reserva_id, pago_id, monto, simulado) "
                + "VALUES (?, ?, ?, 241.50, true)", liquidacion, reserva, pago);
        return reserva;
    }

    @Test
    @DisplayName("HU-19 R42, R44: CA2 el arrendador ve su liquidación PAGADA con operación, fecha, destino enmascarado "
            + "y sus reservas, sin datos del usuario, del pago ni del admin")
    void ca2VeLaPagada() throws Exception {
        var generadaEn = reloj.instant().minus(Duration.ofDays(1));
        var pagadaEn = generadaEn.plus(Duration.ofHours(2));
        var id = pagadaSembrada(arrendadorId, generadaEn, pagadaEn, usuarioId, "OP-00123456");
        var reserva = lineaEn(id, 14);

        var respuesta = misLiquidaciones(token(CORREO_ARRENDADOR), "").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].id").value(id))
                .andExpect(jsonPath("$.liquidaciones[0].periodo")
                        .value(LocalDate.ofInstant(generadaEn, ZonaHoraria.LIMA).toString()))
                .andExpect(jsonPath("$.liquidaciones[0].estado").value("PAGADA"))
                .andExpect(jsonPath("$.liquidaciones[0].montoBruto").value(100.0))
                .andExpect(jsonPath("$.liquidaciones[0].comision").value(10.0))
                .andExpect(jsonPath("$.liquidaciones[0].montoNeto").value(90.0))
                .andExpect(jsonPath("$.liquidaciones[0].pago.numeroOperacion").value("OP-00123456"))
                .andExpect(jsonPath("$.liquidaciones[0].pago.pagadaEn").value(pagadaEn.toString()))
                .andExpect(jsonPath("$.liquidaciones[0].pago.banco").value(BANCO))
                .andExpect(jsonPath("$.liquidaciones[0].pago.cuentaEnmascarada").value("****1234"))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].reservaId").value(reserva))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].espacioNombre").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].fecha").value(hace5Dias().toString()))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].horaInicio").value(14))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].horaFin").value(17))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].monto").value(241.5))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].simulada").value(true))
                .andExpect(jsonPath("$.liquidaciones[0].reservas[0].cancelada").value(false));

        Map<String, Object> liquidacion = lista(cuerpo(respuesta), "liquidaciones").getFirst();
        assertThat(liquidacion).containsOnlyKeys("id", "periodo", "generadaEn", "estado", "montoBruto", "comision",
                "montoNeto", "simulada", "pago", "reservas");
        assertThat(lista(liquidacion, "reservas").getFirst()).containsOnlyKeys("reservaId", "espacioNombre", "fecha",
                "horaInicio", "horaFin", "monto", "simulada", "cancelada");
        assertThat(texto(respuesta)).doesNotContain(CORREO_USUARIO).doesNotContain("4242").doesNotContain("sim-")
                .doesNotContain("usuario").doesNotContain("generadaPor").doesNotContain("pagadaPor");
    }

    @Test
    @DisplayName("HU-19 R42: CA3 el arrendador ve su liquidación PENDIENTE_CUENTA sin datos de pago")
    void ca3VeLaPendiente() throws Exception {
        var id = liquidacionSembrada(arrendadorId, "PENDIENTE_CUENTA", "241.50", reloj.instant(), usuarioId);
        lineaEn(id, 8);

        misLiquidaciones(token(CORREO_ARRENDADOR), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones[0].estado").value("PENDIENTE_CUENTA"))
                .andExpect(jsonPath("$.liquidaciones[0].montoNeto").value(217.35))
                .andExpect(jsonPath("$.liquidaciones[0].pago").doesNotExist())
                .andExpect(jsonPath("$.liquidaciones[0].reservas.length()").value(1));
    }

    @Test
    @DisplayName("HU-19 R42: de la más reciente a la más antigua, de 20 en 20 con hayMas")
    void paginaDeVeinte() throws Exception {
        var base = reloj.instant().minus(Duration.ofDays(2));
        var ids = new ArrayList<Long>();
        for (var i = 0; i < 21; i++) {
            ids.add(liquidacionSembrada(arrendadorId, "GENERADA", "100.00", base.plus(Duration.ofMinutes(i)),
                    usuarioId));
        }
        var sesion = token(CORREO_ARRENDADOR);

        var primera = cuerpo(misLiquidaciones(sesion, "").andExpect(status().isOk()));
        var segunda = cuerpo(misLiquidaciones(sesion, "?pagina=2").andExpect(status().isOk()));

        var esperados = new ArrayList<>(ids).reversed();
        assertThat(lista(primera, "liquidaciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactlyElementsOf(esperados.subList(0, 20));
        assertThat(primera.get("hayMas")).isEqualTo(true);
        assertThat(lista(segunda, "liquidaciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactly(ids.getFirst());
        assertThat(segunda.get("hayMas")).isEqualTo(false);
    }

    @Test
    @DisplayName("HU-19 R6: solo ve las suyas; otro arrendador no las ve aunque mande un id en los parámetros")
    void soloLasDeLaSesion() throws Exception {
        var otro = otroArrendador();
        var mia = liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), usuarioId);
        var suya = liquidacionSembrada(otro, "PENDIENTE_CUENTA", "100.00", reloj.instant(), usuarioId);

        misLiquidaciones(token(CORREO_ARRENDADOR), "?arrendadorId=" + otro).andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].id").value(mia));
        misLiquidaciones(token(OTRO_CORREO_ARRENDADOR), "?arrendadorId=" + arrendadorId).andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].id").value(suya));
    }

    @Test
    @DisplayName("HU-19 R43: una página inválida responde 400 en pagina")
    void paginaInvalida() throws Exception {
        var sesion = token(CORREO_ARRENDADOR);

        for (var pagina : List.of("0", "-1", "abc", "1.5", "99999999999")) {
            misLiquidaciones(sesion, "?pagina=" + pagina).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("pagina"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(MisLiquidacionesService.MENSAJE_PAGINA));
        }
    }

    @Test
    @DisplayName("HU-19 R4: sin sesión responde 401")
    void sinSesion() throws Exception {
        liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), usuarioId);

        misLiquidaciones(null, "").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        misLiquidaciones("token-inventado", "").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HU-19 R5: un USUARIO o un ADMIN recibe 403 antes de validar la página")
    void otroRol() throws Exception {
        liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), usuarioId);
        var admin = tokenAdmin();

        misLiquidaciones(token(CORREO_USUARIO), "?pagina=abc").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        mvc.perform(comoAdmin(admin, get(MIS_LIQUIDACIONES + "?pagina=abc"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
    }

    @Test
    @DisplayName("HU-19 R41: la ruta del arrendador responde Cache-Control no-store")
    void noStore() throws Exception {
        misLiquidaciones(token(CORREO_ARRENDADOR), "").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
