package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.administracion.service.ReclamoAdminService;
import pe.edu.ulima.espacioya.administracion.service.correo.CorreoResolucionArrendador;
import pe.edu.ulima.espacioya.administracion.service.correo.CorreoResolucionUsuario;

/**
 * Panel del admin de HU-26 (CA2, CA3) por la API contra PostgreSQL real y la pasarela simulada, que aprueba los
 * reembolsos de las filas {@code sim-}.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReclamoAdminControllerIT extends ReclamosSoporte {

    private static final String M_MONTO = ReclamoAdminService.MENSAJE_MONTO;
    private static final String M_REEMBOLSO = ReclamoAdminService.MENSAJE_REEMBOLSO;
    private static final String M_A_FAVOR = "Elige a favor de quién se resuelve.";
    private static final String M_RESOLUCION = "Escribe el motivo de la resolución en hasta 500 caracteres.";

    private long admin;

    private String tokenAdmin() throws Exception {
        admin = crearAdmin();
        return token(CORREO_ADMIN);
    }

    private ResultActions listar(String sesion, String consulta) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(URI.create(ADMIN_RECLAMOS + consulta))));
    }

    private ResultActions detalle(String sesion, Object id) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(URI.create(ADMIN_RECLAMOS + "/" + id))));
    }

    private ResultActions resolver(String sesion, Object id, String cuerpo) throws Exception {
        return mvc.perform(comoAdmin(sesion, post(URI.create(ADMIN_RECLAMOS + "/" + id + "/resolucion")))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String aFavorUsuario(String reembolso, String monto) {
        return "{\"aFavorDe\":\"USUARIO\",\"reembolso\":\"" + reembolso + "\""
                + (monto == null ? "" : ",\"montoReembolso\":" + monto) + ",\"resolucion\":\"Se comprobó el cierre\"}";
    }

    private static final String A_FAVOR_ARRENDADOR =
            "{\"aFavorDe\":\"ARRENDADOR\",\"reembolso\":\"TOTAL\",\"montoReembolso\":10,"
                    + "\"resolucion\":\"No se comprobó\"}";

    @SuppressWarnings("unchecked")
    private List<Long> ids(ResultActions respuesta) throws Exception {
        var filas = (List<Map<String, Object>>) cuerpo(respuesta.andExpect(status().isOk())).get("reclamos");
        return filas.stream().map(fila -> numero(fila.get("id"))).toList();
    }

    private Map<String, Object> reembolsoDe(long reserva) {
        return jdbc.queryForMap("SELECT r.* FROM reembolso r JOIN pago p ON p.id = r.pago_id WHERE p.reserva_id = ?",
                reserva);
    }

    private String estadoDePago(long reserva) {
        return jdbc.queryForObject("SELECT estado FROM pago WHERE reserva_id = ?", String.class, reserva);
    }

    private static void esErrorDeCampo(ResultActions respuesta, String campo, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private void sinResolver(long reserva) {
        assertThat(reclamoDe(reserva).get("estado")).isEqualTo("ABIERTO");
        assertThat(filas("reembolso")).isZero();
        assertThat(auditorias("RECLAMO_RESUELTO")).isEmpty();
        assertThat(correos.enviados()).isEmpty();
    }

    // ── Listas y detalle ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R35, R41: los abiertos salen del más antiguo al más nuevo, de a 10, con no-store")
    void abiertos() throws Exception {
        var sesion = tokenAdmin();
        var nuevo = sembrarReclamo(confirmada(hoy.minusDays(1), 8), usuario, arrendador, reloj.instant());
        var viejo = sembrarReclamo(confirmada(hoy.minusDays(1), 9), usuario, arrendador,
                reloj.instant().minus(Duration.ofHours(5)));
        var resuelto = sembrarReclamo(confirmada(hoy.minusDays(1), 10), usuario, arrendador,
                reloj.instant().minus(Duration.ofHours(9)));
        resolverPorJdbc(resuelto, "RESUELTO_ARRENDADOR", null, admin, reloj.instant());

        var respuesta = listar(sesion, "?grupo=abiertos").andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.reclamos[0].espacioNombre").value(NOMBRE_ESPACIO));

        assertThat(ids(respuesta)).containsExactly(viejo, nuevo);
    }

    @Test
    @DisplayName("HU-26 R36: los resueltos salen por resuelto_en descendente, con hayMas")
    void resueltos() throws Exception {
        var sesion = tokenAdmin();
        Long ultimo = null;
        for (var hora = 0; hora < 11; hora++) {
            var id = sembrarReclamo(confirmada(hoy.minusDays(1), hora), usuario, arrendador,
                    reloj.instant().minus(Duration.ofHours(20)));
            resolverPorJdbc(id, hora % 2 == 0 ? "RESUELTO_ARRENDADOR" : "RESUELTO_USUARIO",
                    hora % 2 == 0 ? null : "10.00", admin, reloj.instant().minus(Duration.ofHours(11 - hora)));
            ultimo = id;
        }
        sembrarReclamo(confirmada(hoy.minusDays(1), 12));

        var primera = listar(sesion, "?grupo=resueltos").andExpect(jsonPath("$.hayMas").value(true))
                .andExpect(jsonPath("$.reclamos", hasSize(10)));
        listar(sesion, "?grupo=resueltos&pagina=2").andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.reclamos", hasSize(1)));

        assertThat(ids(primera).getFirst()).isEqualTo(ultimo);
    }

    @Test
    @DisplayName("HU-26 R37, R41: el detalle trae nombres, monto pagado y el hilo, sin correo, teléfono ni tarjeta")
    void detalleAdmin() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);
        sembrarMensaje(id, arrendador, "ARRENDADOR", "Lo revisamos", reloj.instant());

        var respuesta = detalle(sesion, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.usuarioNombre").value(NOMBRE_USUARIO))
                .andExpect(jsonPath("$.arrendadorNombre").value(NOMBRE_ARRENDADOR))
                .andExpect(jsonPath("$.montoPagado").value(241.5))
                .andExpect(jsonPath("$.mensajes[0].texto").value("Lo revisamos"))
                .andExpect(jsonPath("$.descripcion").value("Estaba cerrado"));

        assertThat(cuerpo(respuesta)).doesNotContainKeys("papel", "mensajesRestantes", "puedeEscribir");
        assertThat(respuesta.andReturn().getResponse().getContentAsString())
                .doesNotContain(CORREO_USUARIO, CORREO_ARRENDADOR, TELEFONO_USUARIO, "sim-", "ultimos4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USUARIO", "ARRENDADOR"})
    @DisplayName("HU-26 R38: USUARIO y ARRENDADOR reciben 403 en las tres rutas del admin")
    void otroRol(String rol) throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        var sesion = token("USUARIO".equals(rol) ? CORREO_USUARIO : CORREO_ARRENDADOR);

        listar(sesion, "?grupo=abiertos").andExpect(status().isForbidden());
        detalle(sesion, id).andExpect(status().isForbidden());
        resolver(sesion, id, A_FAVOR_ARRENDADOR).andExpect(status().isForbidden());
        assertThat(reclamoDe(reclamoReserva(id)).get("estado")).isEqualTo("ABIERTO");
    }

    private long reclamoReserva(long reclamo) {
        return jdbc.queryForObject("SELECT reserva_id FROM reclamo WHERE id = ?", Long.class, reclamo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?grupo=", "?grupo=todos", "?grupo=ABIERTOS"})
    @DisplayName("HU-26 R39: grupo ausente o desconocido responde 400 en grupo")
    void grupoInvalido(String consulta) throws Exception {
        esErrorDeCampo(listar(tokenAdmin(), consulta), "grupo", ReclamoAdminService.MENSAJE_GRUPO);
    }

    @Test
    @DisplayName("HU-26 R40: un id inexistente o mal escrito responde 404 en detalle y resolución")
    void noEncontrado() throws Exception {
        var sesion = tokenAdmin();

        detalle(sesion, 999999).andExpect(status().isNotFound());
        detalle(sesion, "abc").andExpect(status().isNotFound());
        resolver(sesion, 999999, A_FAVOR_ARRENDADOR).andExpect(status().isNotFound());
        resolver(sesion, "-1", A_FAVOR_ARRENDADOR).andExpect(status().isNotFound());
    }

    // ── A favor del usuario (CA2) ────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R42, R44, R45, R56: TOTAL reembolsa lo pagado, deja reembolso y pago REEMBOLSADO y la reserva "
            + "CONFIRMADA")
    void total() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        resolver(sesion, id, aFavorUsuario("TOTAL", "5")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estado").value("RESUELTO_USUARIO"))
                .andExpect(jsonPath("$.aFavorDe").value("USUARIO"))
                .andExpect(jsonPath("$.montoReembolso").value(241.5))
                .andExpect(jsonPath("$.resolucion").value("Se comprobó el cierre"))
                .andExpect(jsonPath("$.resueltoEn").value("2026-11-04T15:00:00Z"))
                .andExpect(jsonPath("$.usuarioNombre").value(NOMBRE_USUARIO));

        var fila = reclamoDe(reserva);
        assertThat(fila.get("estado")).isEqualTo("RESUELTO_USUARIO");
        assertThat(decimal(fila.get("monto_reembolso"))).isEqualByComparingTo(MONTO);
        assertThat(numero(fila.get("resuelto_por"))).isEqualTo(admin);
        var reembolso = reembolsoDe(reserva);
        assertThat(reembolso.get("origen")).isEqualTo("RECLAMO");
        assertThat(reembolso.get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(decimal(reembolso.get("monto"))).isEqualByComparingTo(MONTO);
        assertThat(estadoDePago(reserva)).isEqualTo("REEMBOLSADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reserva))
                .isEqualTo("CONFIRMADA");
        assertThat(auditorias("REEMBOLSO_SOLICITADO")).singleElement().satisfies(a -> assertThat(a.get("detalle"))
                .isEqualTo("origen=RECLAMO;reservaId=" + reserva + ";monto=241.50"));
    }

    @Test
    @DisplayName("HU-26 R43, R44: PARCIAL reembolsa el monto escrito y el pago queda REEMBOLSADO")
    void parcial() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        resolver(sesion, id, aFavorUsuario("PARCIAL", "100.25")).andExpect(status().isOk())
                .andExpect(jsonPath("$.montoReembolso").value(100.25));

        assertThat(decimal(reclamoDe(reserva).get("monto_reembolso"))).isEqualByComparingTo("100.25");
        assertThat(decimal(reembolsoDe(reserva).get("monto"))).isEqualByComparingTo("100.25");
        assertThat(reembolsoDe(reserva).get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(estadoDePago(reserva)).isEqualTo("REEMBOLSADO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-5", "241.50", "300", "10.123", "\"20\""})
    @DisplayName("HU-26 R46: un parcial ausente, 0, igual o mayor al pagado, con 3 decimales o como texto responde 400")
    void montoInvalido(String monto) throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        esErrorDeCampo(resolver(sesion, id, aFavorUsuario("PARCIAL", monto)), "montoReembolso", M_MONTO);
        sinResolver(reserva);
    }

    @Test
    @DisplayName("HU-26 R47: a favor del usuario sin reembolso o con uno desconocido responde 400 en reembolso")
    void reembolsoInvalido() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        esErrorDeCampo(resolver(sesion, id, "{\"aFavorDe\":\"USUARIO\",\"resolucion\":\"Ok\"}"), "reembolso",
                M_REEMBOLSO);
        esErrorDeCampo(resolver(sesion, id, aFavorUsuario("MITAD", "10")), "reembolso", M_REEMBOLSO);
        sinResolver(reserva);
    }

    // ── A favor del arrendador (CA3) ─────────────────────────────────────

    @Test
    @DisplayName("HU-26 R49, R51: a favor del arrendador queda RESUELTO_ARRENDADOR sin monto ni reembolso")
    void aFavorDelArrendador() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        resolver(sesion, id, A_FAVOR_ARRENDADOR).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTO_ARRENDADOR"))
                .andExpect(jsonPath("$.aFavorDe").value("ARRENDADOR"))
                .andExpect(jsonPath("$.montoPagado").value(241.5));

        var fila = reclamoDe(reserva);
        assertThat(fila.get("estado")).isEqualTo("RESUELTO_ARRENDADOR");
        assertThat(fila.get("monto_reembolso")).isNull();
        assertThat(fila.get("resolucion")).isEqualTo("No se comprobó");
        assertThat(filas("reembolso")).isZero();
        assertThat(estadoDePago(reserva)).isEqualTo("PAGADO");
    }

    // ── Reglas comunes ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R52, R53: resolución y aFavorDe inválidos responden 400 en su campo")
    void camposInvalidos() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        esErrorDeCampo(resolver(sesion, id, "{\"aFavorDe\":\"ARRENDADOR\",\"resolucion\":\"<b></b>\"}"),
                "resolucion", M_RESOLUCION);
        esErrorDeCampo(resolver(sesion, id, "{\"aFavorDe\":\"ARRENDADOR\",\"resolucion\":\""
                + "a".repeat(501) + "\"}"), "resolucion", M_RESOLUCION);
        esErrorDeCampo(resolver(sesion, id, "{\"aFavorDe\":\"ADMIN\",\"resolucion\":\"Ok\"}"), "aFavorDe",
                M_A_FAVOR);
        esErrorDeCampo(resolver(sesion, id, "{\"resolucion\":\"Ok\"}"), "aFavorDe", M_A_FAVOR);
        sinResolver(reserva);
    }

    @Test
    @DisplayName("HU-26 R18: la resolución se guarda sin < ni >")
    void resolucionSanitizada() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        resolver(sesion, id, "{\"aFavorDe\":\"ARRENDADOR\",\"resolucion\":\"No <script>x</script>procede\"}")
                .andExpect(status().isOk());

        assertThat((String) reclamoDe(reserva).get("resolucion")).doesNotContain("<", ">").startsWith("No");
    }

    @Test
    @DisplayName("HU-26 R54: un reclamo ya resuelto responde ya resuelto sin otro reembolso, auditoría ni correos")
    void yaResuelto() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);
        resolver(sesion, id, A_FAVOR_ARRENDADOR).andExpect(status().isOk());
        correos.reiniciar();

        resolver(sesion, id, aFavorUsuario("TOTAL", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ReclamoAdminService.MENSAJE_YA_RESUELTO));

        assertThat(reclamoDe(reserva).get("estado")).isEqualTo("RESUELTO_ARRENDADOR");
        assertThat(filas("reembolso")).isZero();
        assertThat(auditorias("RECLAMO_RESUELTO")).hasSize(1);
        assertThat(correos.enviados()).isEmpty();
    }

    @Test
    @DisplayName("HU-26 R55: dos resoluciones simultáneas dejan un 200, un 400 ya resuelto y un solo reembolso")
    void dosResolucionesALaVez() throws Exception {
        var sesion = tokenAdmin();
        for (var ronda = 0; ronda < 3; ronda++) {
            var reserva = confirmada(hoy.minusDays(1), 8 + ronda);
            pago(reserva, "PAGADO");
            var id = sembrarReclamo(reserva);

            var respuestas = aLaVez(List.of(() -> resolver(sesion, id, aFavorUsuario("TOTAL", null)).andReturn(),
                    () -> resolver(sesion, id, A_FAVOR_ARRENDADOR).andReturn()));

            assertThat(respuestas).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(200, 400);
            assertThat(auditorias("RECLAMO_RESUELTO")).hasSize(ronda + 1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reembolso r JOIN pago p ON p.id = r.pago_id "
                    + "WHERE p.reserva_id = ?", Integer.class, reserva)).isLessThanOrEqualTo(1);
        }
    }

    // ── Correos y auditoría ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R57, R58: a favor del usuario, el usuario recibe el monto y el arrendador no")
    void correosAFavorDelUsuario() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReclamo(reclamableConPago());

        resolver(sesion, id, aFavorUsuario("PARCIAL", "80")).andExpect(status().isOk());

        assertThat(correos.para(CORREO_USUARIO)).singleElement().satisfies(correo -> {
            assertThat(correo.asunto()).isEqualTo(CorreoResolucionUsuario.ASUNTO);
            assertThat(correo.cuerpo()).contains(NOMBRE_ESPACIO, "Resultado: a tu favor",
                    "Motivo: Se comprobó el cierre", "S/ 80.00");
        });
        assertThat(correos.para(CORREO_ARRENDADOR)).singleElement().satisfies(correo -> {
            assertThat(correo.asunto()).isEqualTo(CorreoResolucionArrendador.ASUNTO);
            assertThat(correo.cuerpo()).contains("a favor del usuario", "Motivo: Se comprobó el cierre")
                    .doesNotContain("S/", NOMBRE_USUARIO, CORREO_USUARIO, TELEFONO_USUARIO);
        });
    }

    @Test
    @DisplayName("HU-26 R57, R58: a favor del arrendador, los dos reciben el resultado sin montos")
    void correosAFavorDelArrendador() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReclamo(reclamableConPago());

        resolver(sesion, id, A_FAVOR_ARRENDADOR).andExpect(status().isOk());

        assertThat(correos.para(CORREO_USUARIO)).singleElement().satisfies(correo -> assertThat(correo.cuerpo())
                .contains("a favor del arrendador").doesNotContain("S/"));
        assertThat(correos.para(CORREO_ARRENDADOR)).singleElement().satisfies(correo -> assertThat(correo.cuerpo())
                .contains("a tu favor. La reserva entra en la siguiente liquidación."));
    }

    @Test
    @DisplayName("HU-26 R59: si la transacción se revierte (reserva sin pago PAGADO) no salen correos ni pasarela")
    void rollbackSinCorreos() throws Exception {
        var sesion = tokenAdmin();
        var reserva = confirmada(hoy.minusDays(1), 8);
        var id = sembrarReclamo(reserva);

        resolver(sesion, id, aFavorUsuario("TOTAL", null)).andExpect(status().isInternalServerError());

        sinResolver(reserva);
    }

    @Test
    @DisplayName("HU-26 R62: audita RECLAMO_RESUELTO con admin, IP y detalle, sin el texto de la resolución")
    void auditaResolucion() throws Exception {
        var sesion = tokenAdmin();
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);

        resolver(sesion, id, aFavorUsuario("PARCIAL", "20.5")).andExpect(status().isOk());

        assertThat(auditorias("RECLAMO_RESUELTO")).singleElement().satisfies(fila -> {
            assertThat(numero(fila.get("usuario_id"))).isEqualTo(admin);
            assertThat(fila.get("entidad")).isEqualTo("reclamo");
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(id);
            assertThat(fila.get("ip")).isEqualTo(IP_ADMIN);
            assertThat(fila.get("detalle")).isEqualTo("reservaId=" + reserva
                    + ";aFavorDe=USUARIO;reembolso=PARCIAL;monto=20.50");
        });
    }

    @Test
    @DisplayName("HU-26 R60, R62: el flujo completo audita apertura y resolución; el reembolso queda visible al admin")
    void flujoCompleto() throws Exception {
        var reserva = reclamableConPago();
        var id = abrirPorLaApi(token(CORREO_USUARIO), reserva);
        var sesion = tokenAdmin();

        resolver(sesion, id, aFavorUsuario("TOTAL", null)).andExpect(status().isOk());

        assertThat(auditorias("RECLAMO_ABIERTO")).hasSize(1);
        assertThat(auditorias("RECLAMO_RESUELTO")).hasSize(1);
        assertThat(decimal(reembolsoDe(reserva).get("monto"))).isEqualTo(new BigDecimal("241.50"));
    }
}
