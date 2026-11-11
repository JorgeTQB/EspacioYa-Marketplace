package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.administracion.dto.DecisionReporteDTO;
import pe.edu.ulima.espacioya.administracion.service.ReporteAdminService;
import pe.edu.ulima.espacioya.administracion.service.correo.CorreoEspacioOcultado;
import pe.edu.ulima.espacioya.espacios.service.EstadoEspacioService;

/**
 * Panel de moderación de HU-27 (CA2, CA3) por la API contra PostgreSQL real. El reloj está fijo en el miércoles
 * 2026-11-04 a las 10:00 de Lima; el admin entra desde 127.0.0.1 con MFA activo.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReporteAdminControllerIT extends ReportesSoporte {

    private static final String MOTIVO = "Las fotos no corresponden al local.";
    private static final String M_PAGINA = "La página no es válida.";

    private long admin;

    private String tokenAdmin() throws Exception {
        admin = crearAdmin();
        return token(CORREO_ADMIN);
    }

    private ResultActions listar(String sesion, String consulta) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(URI.create(ADMIN_REPORTES + consulta))));
    }

    private ResultActions detalle(String sesion, Object id) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(URI.create(ADMIN_REPORTES + "/" + id))));
    }

    private ResultActions ocultar(String sesion, Object id) throws Exception {
        return decidir(sesion, id, "OCULTAR", MOTIVO);
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(ResultActions respuesta) throws Exception {
        var filas = (List<Map<String, Object>>) cuerpo(respuesta.andExpect(status().isOk())).get("reportes");
        return filas.stream().map(fila -> numero(fila.get("id"))).toList();
    }

    private static void esErrorDeCampo(ResultActions respuesta, String campo, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private static void esRechazo(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    private void sinDecision(long reporte) {
        assertThat(reporteGuardado(reporte).get("estado")).isEqualTo("POR_REVISAR");
        assertThat(auditorias("REPORTE_RESUELTO")).isEmpty();
        assertThat(auditorias("OCULTACION_ESPACIO")).isEmpty();
        assertThat(auditorias("OCULTACION_RESENA")).isEmpty();
        assertThat(correos.enviados()).isEmpty();
    }

    /** Horario de 06:00 a 22:00 todos los días: el espacio aparece en la búsqueda de mañana. */
    private void conHorario(long enEspacio) {
        for (var dia = 1; dia <= 7; dia++) {
            jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) "
                    + "VALUES (?, ?, 6, 22)", enEspacio, dia);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> enLaBusqueda() throws Exception {
        var cuerpo = cuerpo(mvc.perform(con(null, get(URI.create("/api/espacios?tipo=CANCHA&zona=LOS_OLIVOS&fecha="
                + hoy.plusDays(1))))).andExpect(status().isOk()));
        return ((List<Map<String, Object>>) cuerpo.get("resultados")).stream().map(r -> numero(r.get("id"))).toList();
    }

    // ── Cola y detalle (CA2) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R20, R28: por revisar salen del más antiguo al más nuevo, de a 10, con no-store")
    void porRevisar() throws Exception {
        var sesion = tokenAdmin();
        var otro = crearUsuario(CORREO_OTRO);
        var resena = resena(otro, espacio, "VISIBLE");
        var nuevo = sembrarReporte(usuario, "ESPACIO", espacio, "SPAM", reloj.instant());
        var viejo = sembrarReporte(usuario, "RESENA", resena, "FRAUDE", reloj.instant().minus(Duration.ofHours(5)));
        var resuelto = sembrarReporte(otro, "ESPACIO", espacio, "SPAM", reloj.instant().minus(Duration.ofHours(9)));
        resolverReportePorJdbc(resuelto, "MANTENIDO", admin, reloj.instant());

        var respuesta = listar(sesion, "?grupo=pendientes").andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.reportes[0].tipoObjetivo").value("RESENA"))
                .andExpect(jsonPath("$.reportes[0].objetivoId").value(resena))
                .andExpect(jsonPath("$.reportes[0].espacioNombre").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.reportes[0].motivo").value("FRAUDE"))
                .andExpect(jsonPath("$.reportes[0].estado").value("POR_REVISAR"))
                .andExpect(jsonPath("$.reportes[0].resueltoEn").doesNotExist())
                .andExpect(jsonPath("$.reportes[0].reportanteId").doesNotExist());

        assertThat(ids(respuesta)).containsExactly(viejo, nuevo);
    }

    @Test
    @DisplayName("HU-27 R20: la cola pagina de a 10 con hayMas")
    void paginacion() throws Exception {
        var sesion = tokenAdmin();
        for (var i = 0; i < 11; i++) {
            sembrarReporte(usuario, "ESPACIO", espacioDe(arrendador), "SPAM",
                    reloj.instant().minus(Duration.ofMinutes(11 - i)));
        }

        listar(sesion, "?grupo=pendientes&pagina=1").andExpect(jsonPath("$.reportes", hasSize(10)))
                .andExpect(jsonPath("$.hayMas").value(true));
        listar(sesion, "?grupo=pendientes&pagina=2").andExpect(jsonPath("$.reportes", hasSize(1)))
                .andExpect(jsonPath("$.hayMas").value(false));
    }

    @Test
    @DisplayName("HU-27 R21: resueltos salen del más reciente al más antiguo por resuelto_en")
    void resueltos() throws Exception {
        var sesion = tokenAdmin();
        var otro = crearUsuario(CORREO_OTRO);
        var antes = sembrarReporte(usuario, "ESPACIO", espacio, "SPAM", reloj.instant().minus(Duration.ofHours(9)));
        var despues = sembrarReporte(otro, "ESPACIO", espacio, "SPAM", reloj.instant().minus(Duration.ofHours(9)));
        sembrarReporte(usuario, "ESPACIO", espacioDe(arrendador), "SPAM", reloj.instant());
        resolverReportePorJdbc(antes, "OCULTADO", admin, reloj.instant().minus(Duration.ofHours(2)));
        resolverReportePorJdbc(despues, "MANTENIDO", admin, reloj.instant().minus(Duration.ofHours(1)));

        var respuesta = listar(sesion, "?grupo=resueltos").andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reportes[0].estado").value("MANTENIDO"))
                .andExpect(jsonPath("$.reportes[0].resueltoEn").value("2026-11-04T14:00:00Z"));

        assertThat(ids(respuesta)).containsExactly(despues, antes);
    }

    @Test
    @DisplayName("HU-27 R22, R23: grupo o página inválidos dan 400 en su campo")
    void grupoYPaginaInvalidos() throws Exception {
        var sesion = tokenAdmin();

        esErrorDeCampo(listar(sesion, ""), "grupo", ReporteAdminService.MENSAJE_GRUPO);
        esErrorDeCampo(listar(sesion, "?grupo=abiertos"), "grupo", ReporteAdminService.MENSAJE_GRUPO);
        esErrorDeCampo(listar(sesion, "?grupo=pendientes&pagina=0"), "pagina", M_PAGINA);
        esErrorDeCampo(listar(sesion, "?grupo=pendientes&pagina=abc"), "pagina", M_PAGINA);
        esErrorDeCampo(listar(sesion, "?grupo=resueltos&pagina=2147483648"), "pagina", M_PAGINA);
        listar(sesion, "?grupo=resueltos&pagina=2147483647").andExpect(status().isOk())
                .andExpect(jsonPath("$.reportes", hasSize(0)));
    }

    @Test
    @DisplayName("HU-27 R24, R28: el detalle de un espacio trae su estado actual y el nombre del arrendador, sin "
            + "correo, teléfono ni reportante")
    void detalleDeEspacio() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio, "INFORMACION_FALSA", reloj.instant());
        sembrarReporte(crearUsuario(CORREO_OTRO), "ESPACIO", espacio);

        var respuesta = detalle(sesion, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tipoObjetivo").value("ESPACIO"))
                .andExpect(jsonPath("$.objetivoId").value(espacio))
                .andExpect(jsonPath("$.motivo").value("INFORMACION_FALSA"))
                .andExpect(jsonPath("$.estado").value("POR_REVISAR"))
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andExpect(jsonPath("$.reportesPendientes").value(2))
                .andExpect(jsonPath("$.espacio.nombre").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.espacio.tipo").value("CANCHA"))
                .andExpect(jsonPath("$.espacio.zona").value("LOS_OLIVOS"))
                .andExpect(jsonPath("$.espacio.capacidad").value(22))
                .andExpect(jsonPath("$.espacio.precioHora").value(80.5))
                .andExpect(jsonPath("$.espacio.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.espacio.arrendadorNombre").value(NOMBRE_ARRENDADOR))
                .andExpect(jsonPath("$.resena").doesNotExist())
                .andExpect(jsonPath("$.reservasCanceladas").doesNotExist());

        var texto = respuesta.andReturn().getResponse().getContentAsString();
        assertThat(texto).doesNotContain(CORREO_ARRENDADOR, CORREO_USUARIO, "999111222", "reportante");
    }

    @Test
    @DisplayName("HU-27 R25: el detalle de una reseña trae la reseña y su espacio, sin su autor ni el reportante")
    void detalleDeResena() throws Exception {
        var sesion = tokenAdmin();
        var autor = crearUsuario(CORREO_OTRO);
        jdbc.update("UPDATE usuario SET nombre = 'Beto Autor' WHERE id = ?", autor);
        var resena = resena(autor, espacio, "VISIBLE");
        var id = sembrarReporte(usuario, "RESENA", resena);

        var respuesta = detalle(sesion, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoObjetivo").value("RESENA"))
                .andExpect(jsonPath("$.resena.calificacion").value(4))
                .andExpect(jsonPath("$.resena.comentario").value(COMENTARIO))
                .andExpect(jsonPath("$.resena.estado").value("VISIBLE"))
                .andExpect(jsonPath("$.resena.espacioId").value(espacio))
                .andExpect(jsonPath("$.resena.espacioNombre").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.resena.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.espacio").doesNotExist());

        assertThat(respuesta.andReturn().getResponse().getContentAsString())
                .doesNotContain("Beto Autor", CORREO_OTRO, CORREO_USUARIO);
    }

    @Test
    @DisplayName("HU-27 R27: un id inexistente o mal escrito da 404 en el detalle y en la decisión")
    void inexistente() throws Exception {
        var sesion = tokenAdmin();

        for (var id : new String[] {"999999", "abc", "0", "-1", "99999999999999999999"}) {
            detalle(sesion, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            decidir(sesion, id, "MANTENER", MOTIVO).andExpect(status().isNotFound());
        }
    }

    // ── Acceso (STRIDE E/S) ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R6: sin sesión válida las rutas del admin responden 401")
    void sinSesion() throws Exception {
        var id = sembrarReporte(usuario, "ESPACIO", espacio);

        listar(null, "?grupo=pendientes").andExpect(status().isUnauthorized());
        detalle(null, id).andExpect(status().isUnauthorized());
        decidir(null, id, "OCULTAR", MOTIVO).andExpect(status().isUnauthorized());
        decidir("token-inventado", id, "OCULTAR", MOTIVO).andExpect(status().isUnauthorized());
        sinDecision(id);
    }

    @Test
    @DisplayName("HU-27 R26: un USUARIO o un ARRENDADOR reciben 403 en las rutas del admin")
    void otrosRoles() throws Exception {
        var id = sembrarReporte(usuario, "ESPACIO", espacio);

        for (var correo : new String[] {CORREO_USUARIO, CORREO_ARRENDADOR}) {
            var sesion = token(correo);
            listar(sesion, "?grupo=pendientes").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
            detalle(sesion, id).andExpect(status().isForbidden());
            ocultar(sesion, id).andExpect(status().isForbidden());
        }
        sinDecision(id);
        assertThat(estadoDe("espacio", espacio)).isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("HU-27 R29: desde una IP no permitida, aun con sesión de admin, responden 403 sin decidir")
    void ipNoPermitida() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);

        mvc.perform(con(sesion, get(URI.create(ADMIN_REPORTES + "?grupo=pendientes"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        mvc.perform(con(sesion, get(URI.create(ADMIN_REPORTES + "/" + id)))).andExpect(status().isForbidden());
        mvc.perform(con(sesion, post(URI.create(ADMIN_REPORTES + "/" + id + "/decision")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"OCULTAR\",\"motivoDecision\":\"x\"}"))
                .andExpect(status().isForbidden());
        sinDecision(id);
    }

    @Test
    @DisplayName("HU-27 R30: la sesión de un admin sin MFA activo responde 401")
    void adminSinMfa() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        jdbc.update("UPDATE mfa SET activo = false, activado_en = NULL WHERE usuario_id = ?", admin);

        listar(sesion, "?grupo=pendientes").andExpect(status().isUnauthorized());
        ocultar(sesion, id).andExpect(status().isUnauthorized());
        sinDecision(id);
    }

    @Test
    @DisplayName("HU-27 R55: no hay rutas para editar ni borrar un reporte o su decisión (405)")
    void sinEdicion() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);

        mvc.perform(comoAdmin(sesion, put(URI.create(ADMIN_REPORTES + "/" + id))).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(sesion, delete(URI.create(ADMIN_REPORTES + "/" + id))))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(sesion, put(URI.create(ADMIN_REPORTES + "/" + id + "/decision")))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(comoAdmin(sesion, delete(URI.create(ADMIN_REPORTES + "/" + id + "/decision"))))
                .andExpect(status().isMethodNotAllowed());
        sinDecision(id);
    }

    // ── Validación de la decisión ────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R43, R44: decisión o motivo inválidos (en blanco, 501, solo etiquetas) dan 400 en su campo")
    void decisionInvalida() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);

        esErrorDeCampo(decidir(sesion, id, "ELIMINAR", MOTIVO), "decision", DecisionReporteDTO.M_DECISION);
        esErrorDeCampo(decidir(sesion, id, "{\"motivoDecision\":\"x\"}"), "decision", DecisionReporteDTO.M_DECISION);
        esErrorDeCampo(decidir(sesion, id, "{\"decision\":1,\"motivoDecision\":\"x\"}"), "decision",
                DecisionReporteDTO.M_DECISION);
        esErrorDeCampo(decidir(sesion, id, "OCULTAR", "   "), "motivoDecision", DecisionReporteDTO.M_MOTIVO_DECISION);
        esErrorDeCampo(decidir(sesion, id, "OCULTAR", "a".repeat(501)), "motivoDecision",
                DecisionReporteDTO.M_MOTIVO_DECISION);
        esErrorDeCampo(decidir(sesion, id, "OCULTAR", "<b></b><i></i>"), "motivoDecision",
                DecisionReporteDTO.M_MOTIVO_DECISION);
        esErrorDeCampo(decidir(sesion, id, "{\"decision\":\"OCULTAR\",\"motivoDecision\":5}"), "motivoDecision",
                DecisionReporteDTO.M_MOTIVO_DECISION);
        sinDecision(id);
        assertThat(estadoDe("espacio", espacio)).isEqualTo("PUBLICADO");
    }

    // ── Ocultar un espacio (CA3) ─────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R31, R32, R33, R35, R36, R45, R46, R47: ocultar un espacio con 3 reportes lo saca del catálogo, "
            + "resuelve los 3, mantiene sus reservas y avisa al arrendador")
    void ocultaEspacio() throws Exception {
        conHorario(espacio);
        var sesion = tokenAdmin();
        var otro = crearUsuario(CORREO_OTRO);
        var tercero = crearUsuario("tercero.reporte@correo.pe");
        var id = sembrarReporte(usuario, "ESPACIO", espacio, "FRAUDE", reloj.instant().minusSeconds(60));
        sembrarReporte(otro, "ESPACIO", espacio, "SPAM", reloj.instant().minusSeconds(30));
        sembrarReporte(tercero, "ESPACIO", espacio, "INFORMACION_FALSA", reloj.instant());
        var confirmada = confirmada(hoy.plusDays(2), 10);
        var sesionUsuario = token(CORREO_USUARIO);
        assertThat(enLaBusqueda()).contains(espacio);

        ocultar(sesion, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estado").value("OCULTADO"))
                .andExpect(jsonPath("$.decision").value("OCULTAR"))
                .andExpect(jsonPath("$.motivoDecision").value(MOTIVO))
                .andExpect(jsonPath("$.resueltoEn").value("2026-11-04T15:00:00Z"))
                .andExpect(jsonPath("$.reportesPendientes").value(0))
                .andExpect(jsonPath("$.espacio.estado").value("OCULTO"))
                .andExpect(jsonPath("$.reservasCanceladas").value(0));

        assertThat(estadoDe("espacio", espacio)).isEqualTo("OCULTO");
        assertThat(jdbc.queryForList("SELECT * FROM reporte")).hasSize(3).allSatisfy(fila -> {
            assertThat(fila.get("estado")).isEqualTo("OCULTADO");
            assertThat(numero(fila.get("admin_id"))).isEqualTo(admin);
            assertThat(fila.get("motivo_decision")).isEqualTo(MOTIVO);
            assertThat(fila.get("resuelto_en")).isNotNull();
            assertThat(fila.get("por_fraude")).isEqualTo(false);
        });
        assertThat(enLaBusqueda()).doesNotContain(espacio);
        mvc.perform(con(null, get(URI.create("/api/espacios/" + espacio)))).andExpect(status().isNotFound());
        mvc.perform(con(sesionUsuario, post(URI.create("/api/reservas"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"espacioId\":" + espacio + ",\"fecha\":\"" + hoy.plusDays(3)
                                + "\",\"horas\":[10]}"))
                .andExpect(status().isNotFound());
        assertThat(estadoDe("reserva", confirmada)).isEqualTo("CONFIRMADA");
        assertThat(filas("reembolso")).isZero();

        var enviados = correos.para(CORREO_ARRENDADOR);
        assertThat(enviados).singleElement().satisfies(correo -> {
            assertThat(correo.asunto()).isEqualTo(CorreoEspacioOcultado.ASUNTO);
            assertThat(correo.cuerpo()).contains(NOMBRE_ESPACIO, "Motivo: " + MOTIVO, "se mantienen")
                    .doesNotContain("FRAUDE", CORREO_USUARIO);
        });
        assertThat(correos.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("HU-27 R51, R52: ocultar un espacio audita OCULTACION_ESPACIO y REPORTE_RESUELTO sin el motivo")
    void auditaLaOcultacion() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        sembrarReporte(crearUsuario(CORREO_OTRO), "ESPACIO", espacio);

        ocultar(sesion, id).andExpect(status().isOk());

        var ocultacion = auditorias("OCULTACION_ESPACIO");
        assertThat(ocultacion).singleElement().satisfies(fila -> {
            assertThat(fila.get("entidad")).isEqualTo("espacio");
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(espacio);
            assertThat(numero(fila.get("usuario_id"))).isEqualTo(admin);
            assertThat(fila.get("ip")).isEqualTo(IP_ADMIN);
            assertThat(fila.get("detalle")).isEqualTo("estado=PUBLICADO->OCULTO;actor=ADMIN;arrendadorId="
                    + arrendador);
        });
        assertThat(auditorias("REPORTE_RESUELTO")).singleElement().satisfies(fila -> {
            assertThat(fila.get("entidad")).isEqualTo("reporte");
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(id);
            assertThat(fila.get("detalle")).isEqualTo("tipo=ESPACIO;objetivoId=" + espacio
                    + ";decision=OCULTAR;reportes=2;reservasCanceladas=0");
        });
    }

    @Test
    @DisplayName("HU-27 R38: un espacio OCULTO no se edita, pausa, reanuda ni da de baja por el arrendador ni por el "
            + "admin")
    void ocultoEsFinal() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        ocultar(sesion, id).andExpect(status().isOk());
        jdbc.update("UPDATE usuario SET correo_verificado = true WHERE id = ?", arrendador);
        var sesionArrendador = token(CORREO_ARRENDADOR);
        var propio = "/api/arrendadores/actual/espacios/" + espacio;

        mvc.perform(con(sesionArrendador, put(URI.create(propio))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Otro\",\"tipo\":\"CANCHA\",\"direccion\":\"Av. 1\",\"zona\":\"LOS_OLIVOS\","
                        + "\"capacidad\":10,\"precioHora\":50,\"descripcion\":\"Otra\",\"caracteristicas\":[]}"))
                .andExpect(status().isBadRequest());
        for (var accion : new String[] {"/pausa", "/reanudacion", "/baja"}) {
            mvc.perform(con(sesionArrendador, post(URI.create(propio + accion)))).andExpect(status().isBadRequest());
        }
        mvc.perform(comoAdmin(sesion, post(URI.create("/api/admin/espacios/" + espacio + "/pausa"))))
                .andExpect(status().isBadRequest());
        mvc.perform(comoAdmin(sesion, post(URI.create("/api/admin/espacios/" + espacio + "/baja"))))
                .andExpect(status().isBadRequest());
        assertThat(estadoDe("espacio", espacio)).isEqualTo("OCULTO");
        assertThat(jdbc.queryForObject("SELECT nombre FROM espacio WHERE id = ?", String.class, espacio))
                .isEqualTo(NOMBRE_ESPACIO);
    }

    @Test
    @DisplayName("HU-27 R39: ocultar un espacio en BAJA da 400 no ocultable y el reporte sigue POR_REVISAR")
    void espacioEnBaja() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        jdbc.update("UPDATE espacio SET estado = 'BAJA' WHERE id = ?", espacio);

        esRechazo(ocultar(sesion, id), EstadoEspacioService.MENSAJE_NO_OCULTABLE);
        esRechazo(decidir(sesion, id, "OCULTAR_POR_FRAUDE", MOTIVO), EstadoEspacioService.MENSAJE_NO_OCULTABLE);
        sinDecision(id);
        assertThat(estadoDe("espacio", espacio)).isEqualTo("BAJA");
    }

    @Test
    @DisplayName("HU-27 R41: una segunda decisión sobre el mismo reporte da 400 ya resuelto sin cambiar nada")
    void segundaDecision() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        decidir(sesion, id, "MANTENER", MOTIVO).andExpect(status().isOk());

        esRechazo(ocultar(sesion, id), ReporteAdminService.MENSAJE_YA_RESUELTO);
        assertThat(reporteGuardado(id).get("estado")).isEqualTo("MANTENIDO");
        assertThat(estadoDe("espacio", espacio)).isEqualTo("PUBLICADO");
        assertThat(auditorias("REPORTE_RESUELTO")).hasSize(1);
        assertThat(correos.enviados()).isEmpty();
    }

    @Test
    @DisplayName("HU-27 R42: dos decisiones simultáneas sobre reportes hermanos: una 200 y la otra 400 ya resuelto")
    void decisionesSimultaneas() throws Exception {
        var sesion = tokenAdmin();
        var uno = sembrarReporte(usuario, "ESPACIO", espacio);
        var dos = sembrarReporte(crearUsuario(CORREO_OTRO), "ESPACIO", espacio);
        List<Callable<MvcResult>> solicitudes = List.of(
                () -> ocultar(sesion, uno).andReturn(),
                () -> decidir(sesion, dos, "MANTENER", MOTIVO).andReturn());

        var respuestas = aLaVez(solicitudes);

        assertThat(respuestas.stream().map(r -> r.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(200, 400);
        assertThat(auditorias("REPORTE_RESUELTO")).hasSize(1);
        var estados = jdbc.queryForList("SELECT DISTINCT estado FROM reporte", String.class);
        assertThat(estados).hasSize(1).first().isIn("OCULTADO", "MANTENIDO");
    }

    // ── Ocultar una reseña y mantener ────────────────────────────────────

    @Test
    @DisplayName("HU-27 R34, R37, R49, R53: ocultar una reseña la saca de la ficha y del promedio, sin correo")
    void ocultaResena() throws Exception {
        var sesion = tokenAdmin();
        var autor = crearUsuario(CORREO_OTRO);
        var mala = resena(autor, espacio, "VISIBLE");
        var buena = resena(usuario, espacio, "VISIBLE", 10);
        jdbc.update("UPDATE resena SET calificacion = 1 WHERE id = ?", mala);
        jdbc.update("UPDATE resena SET calificacion = 5 WHERE id = ?", buena);
        var id = sembrarReporte(arrendador, "RESENA", mala);
        mvc.perform(con(null, get(URI.create("/api/espacios/" + espacio)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResenas").value(2));

        ocultar(sesion, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OCULTADO"))
                .andExpect(jsonPath("$.resena.estado").value("OCULTA"));

        assertThat(estadoDe("resena", mala)).isEqualTo("OCULTA");
        mvc.perform(con(null, get(URI.create("/api/espacios/" + espacio + "/resenas")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas", hasSize(1)))
                .andExpect(jsonPath("$.resenas[0].id").value(buena));
        mvc.perform(con(null, get(URI.create("/api/espacios/" + espacio)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResenas").value(1))
                .andExpect(jsonPath("$.calificacionPromedio").value(5.0));
        assertThat(correos.enviados()).isEmpty();
        assertThat(auditorias("OCULTACION_RESENA")).singleElement().satisfies(fila -> {
            assertThat(fila.get("entidad")).isEqualTo("resena");
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(mala);
            assertThat(fila.get("detalle")).isEqualTo("estado=VISIBLE->OCULTA;actor=ADMIN;espacioId=" + espacio);
        });
    }

    @Test
    @DisplayName("HU-27 R31, R49: mantener resuelve los reportes como MANTENIDO, no cambia el contenido ni envía correo")
    void mantiene() throws Exception {
        var sesion = tokenAdmin();
        var id = sembrarReporte(usuario, "ESPACIO", espacio);
        var hermano = sembrarReporte(crearUsuario(CORREO_OTRO), "ESPACIO", espacio);

        decidir(sesion, id, "MANTENER", "No infringe las reglas.").andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("MANTENER"))
                .andExpect(jsonPath("$.reservasCanceladas").value(0));

        assertThat(reporteGuardado(id).get("estado")).isEqualTo("MANTENIDO");
        assertThat(reporteGuardado(hermano).get("estado")).isEqualTo("MANTENIDO");
        assertThat(reporteGuardado(hermano).get("motivo_decision")).isEqualTo("No infringe las reglas.");
        assertThat(estadoDe("espacio", espacio)).isEqualTo("PUBLICADO");
        assertThat(correos.enviados()).isEmpty();
        assertThat(auditorias("OCULTACION_ESPACIO")).isEmpty();
    }
}
