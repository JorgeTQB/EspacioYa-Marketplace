package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import pe.edu.ulima.espacioya.administracion.dto.CrearReporteDTO;
import pe.edu.ulima.espacioya.administracion.service.ReporteService;
import pe.edu.ulima.espacioya.comun.error.ErrorDTO;

/**
 * HU-27 CA1 (reportar) por la API contra PostgreSQL real. El reloj está fijo en el miércoles 2026-11-04 a las 10:00
 * de Lima; el cupo de 10 en 24 h se prueba sembrando reportes con su {@code creado_en}.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReporteControllerIT extends ReportesSoporte {

    private static void esRechazo(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    private static void esErrorDeCampo(ResultActions respuesta, String campo, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private static void esNoEncontrado(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    private void sinReporte() {
        assertThat(filas("reporte")).isZero();
        assertThat(auditorias("REPORTE_CREADO")).isEmpty();
    }

    // ── Reportar (CA1) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R1, R3: un usuario reporta un espacio visible: 201 y el reporte POR_REVISAR guardado")
    void usuarioReportaEspacio() throws Exception {
        var respuesta = reportar(token(CORREO_USUARIO), "ESPACIO", espacio, "FRAUDE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoObjetivo").value("ESPACIO"))
                .andExpect(jsonPath("$.objetivoId").value(espacio))
                .andExpect(jsonPath("$.motivo").value("FRAUDE"))
                .andExpect(jsonPath("$.estado").value("POR_REVISAR"))
                .andExpect(jsonPath("$.creadoEn").value("2026-11-04T15:00:00Z"))
                .andExpect(jsonPath("$.reportanteId").doesNotExist());

        var guardado = reporteGuardado(numero(cuerpo(respuesta).get("id")));
        assertThat(guardado.get("tipo_objetivo")).isEqualTo("ESPACIO");
        assertThat(numero(guardado.get("espacio_id"))).isEqualTo(espacio);
        assertThat(guardado.get("resena_id")).isNull();
        assertThat(numero(guardado.get("reportante_id"))).isEqualTo(usuario);
        assertThat(guardado.get("estado")).isEqualTo("POR_REVISAR");
        assertThat(guardado.get("admin_id")).isNull();
        assertThat(guardado.get("por_fraude")).isEqualTo(false);
    }

    @Test
    @DisplayName("HU-27 R2, R3: un usuario reporta una reseña ajena y un arrendador una reseña sobre su espacio")
    void reportanResena() throws Exception {
        var autor = crearUsuario(CORREO_OTRO);
        var resena = resena(autor, espacio, "VISIBLE");

        reportar(token(CORREO_USUARIO), "RESENA", resena, "SPAM").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoObjetivo").value("RESENA"))
                .andExpect(jsonPath("$.objetivoId").value(resena));
        reportar(token(CORREO_ARRENDADOR), "RESENA", resena, "CONTENIDO_INAPROPIADO")
                .andExpect(status().isCreated());

        var filas = jdbc.queryForList("SELECT * FROM reporte ORDER BY id");
        assertThat(filas).hasSize(2);
        assertThat(filas).allSatisfy(fila -> {
            assertThat(numero(fila.get("resena_id"))).isEqualTo(resena);
            assertThat(fila.get("espacio_id")).isNull();
        });
        assertThat(numero(filas.get(1).get("reportante_id"))).isEqualTo(arrendador);
    }

    @Test
    @DisplayName("HU-27 R1: un arrendador reporta el espacio de otro arrendador")
    void arrendadorReportaEspacioAjeno() throws Exception {
        var otro = crearArrendador(CORREO_OTRO_ARRENDADOR);
        var ajeno = espacioDe(otro);

        reportar(token(CORREO_ARRENDADOR), "ESPACIO", ajeno, "INFORMACION_FALSA").andExpect(status().isCreated());

        assertThat(numero(jdbc.queryForMap("SELECT * FROM reporte").get("reportante_id"))).isEqualTo(arrendador);
    }

    @Test
    @DisplayName("HU-27 R4: ignora reportanteId, usuarioId, estado, adminId y otros campos extra del cuerpo")
    void camposExtraIgnorados() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var admin = crearAdmin();

        reportar(token(CORREO_USUARIO), "{\"tipoObjetivo\":\"ESPACIO\",\"objetivoId\":" + espacio
                + ",\"motivo\":\"SPAM\",\"reportanteId\":" + otro + ",\"usuarioId\":" + otro
                + ",\"estado\":\"OCULTADO\",\"adminId\":" + admin + ",\"porFraude\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("POR_REVISAR"));

        var guardado = jdbc.queryForMap("SELECT * FROM reporte");
        assertThat(numero(guardado.get("reportante_id"))).isEqualTo(usuario);
        assertThat(guardado.get("estado")).isEqualTo("POR_REVISAR");
        assertThat(guardado.get("admin_id")).isNull();
        assertThat(guardado.get("por_fraude")).isEqualTo(false);
    }

    @Test
    @DisplayName("HU-27 R5: un ADMIN recibe 403 sin crear el reporte")
    void adminRecibe403() throws Exception {
        crearAdmin();

        mvc.perform(comoAdmin(token(CORREO_ADMIN), post(REPORTES)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoObjetivo\":\"ESPACIO\",\"objetivoId\":" + espacio + ",\"motivo\":\"SPAM\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R6: sin sesión válida responde 401")
    void sinSesion401() throws Exception {
        reportar(null, "ESPACIO", espacio, "SPAM").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        reportar("token-inventado", "ESPACIO", espacio, "SPAM").andExpect(status().isUnauthorized());
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R7: el mismo 404 para espacio inexistente, PAUSADO o de arrendador suspendido y reseña OCULTA")
    void noReportableDa404() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var autor = crearUsuario(CORREO_OTRO);
        var oculta = resena(autor, espacio, "OCULTA");
        var pausado = espacioDe(arrendador);
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", pausado);
        var otro = crearArrendador(CORREO_OTRO_ARRENDADOR);
        var deSuspendido = espacioDe(otro);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", otro);
        var resenaDePausado = resena(autor, pausado, "VISIBLE");

        esNoEncontrado(reportar(sesion, "ESPACIO", espacio + 100000, "SPAM"));
        esNoEncontrado(reportar(sesion, "ESPACIO", pausado, "SPAM"));
        esNoEncontrado(reportar(sesion, "ESPACIO", deSuspendido, "SPAM"));
        esNoEncontrado(reportar(sesion, "RESENA", oculta, "SPAM"));
        esNoEncontrado(reportar(sesion, "RESENA", oculta + 100000, "SPAM"));
        esNoEncontrado(reportar(sesion, "RESENA", resenaDePausado, "SPAM"));
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R8: el arrendador no puede reportar su propio espacio (400 contenido propio)")
    void espacioPropio() throws Exception {
        esRechazo(reportar(token(CORREO_ARRENDADOR), "ESPACIO", espacio, "SPAM"),
                ReporteService.MENSAJE_CONTENIDO_PROPIO);
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R9: el autor no puede reportar su propia reseña (400 contenido propio)")
    void resenaPropia() throws Exception {
        var propia = resena(usuario, espacio, "VISIBLE");

        esRechazo(reportar(token(CORREO_USUARIO), "RESENA", propia, "SPAM"), ReporteService.MENSAJE_CONTENIDO_PROPIO);
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R10: reportar dos veces el mismo contenido da 400 ya reportado, aunque ya esté resuelto")
    void yaReportado() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var id = reportarPorLaApi(sesion, "ESPACIO", espacio);

        esRechazo(reportar(sesion, "ESPACIO", espacio, "FRAUDE"), ReporteService.MENSAJE_YA_REPORTADO);
        resolverReportePorJdbc(id, "MANTENIDO", crearAdmin(), reloj.instant());
        esRechazo(reportar(sesion, "ESPACIO", espacio, "FRAUDE"), ReporteService.MENSAJE_YA_REPORTADO);
        assertThat(filas("reporte")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-27 R11, R12: dos reportes simultáneos iguales: uno 201 y el otro 400 ya reportado, nunca 500")
    void dosSimultaneos() throws Exception {
        var sesion = token(CORREO_USUARIO);
        List<Callable<MvcResult>> solicitudes = new ArrayList<>();
        for (var i = 0; i < 2; i++) {
            solicitudes.add(() -> reportar(sesion, "ESPACIO", espacio, "SPAM").andReturn());
        }

        var codigos = aLaVez(solicitudes).stream().map(r -> r.getResponse().getStatus()).toList();

        assertThat(codigos).containsExactlyInAnyOrder(201, 400);
        assertThat(filas("reporte")).isEqualTo(1);
        assertThat(auditorias("REPORTE_CREADO")).hasSize(1);
    }

    // ── Validación (STRIDE T) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R13: tipoObjetivo ausente, número o fuera de la lista da 400 en su campo")
    void tipoInvalido() throws Exception {
        var sesion = token(CORREO_USUARIO);

        esErrorDeCampo(reportar(sesion, "{\"objetivoId\":" + espacio + ",\"motivo\":\"SPAM\"}"), "tipoObjetivo",
                CrearReporteDTO.M_TIPO);
        esErrorDeCampo(reportar(sesion, "{\"tipoObjetivo\":3,\"objetivoId\":" + espacio + ",\"motivo\":\"SPAM\"}"),
                "tipoObjetivo", CrearReporteDTO.M_TIPO);
        esErrorDeCampo(reportar(sesion, "FOTO", espacio, "SPAM"), "tipoObjetivo", CrearReporteDTO.M_TIPO);
        esErrorDeCampo(reportar(sesion, "espacio", espacio, "SPAM"), "tipoObjetivo", CrearReporteDTO.M_TIPO);
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R14: objetivoId ausente, texto, decimal, 0, negativo o fuera de long da 400 en su campo")
    void objetivoInvalido() throws Exception {
        var sesion = token(CORREO_USUARIO);
        for (var valor : List.of("null", "\"" + espacio + "\"", "1.5", "0", "-3", "99999999999999999999")) {
            esErrorDeCampo(reportar(sesion, "{\"tipoObjetivo\":\"ESPACIO\",\"objetivoId\":" + valor
                    + ",\"motivo\":\"SPAM\"}"), "objetivoId", CrearReporteDTO.M_OBJETIVO);
        }
        esErrorDeCampo(reportar(sesion, "{\"tipoObjetivo\":\"ESPACIO\",\"motivo\":\"SPAM\"}"), "objetivoId",
                CrearReporteDTO.M_OBJETIVO);
        sinReporte();
    }

    @Test
    @DisplayName("HU-27 R15: motivo ausente o fuera del catálogo da 400 en su campo")
    void motivoInvalido() throws Exception {
        var sesion = token(CORREO_USUARIO);

        esErrorDeCampo(reportar(sesion, "ESPACIO", espacio, "OTRO"), "motivo", CrearReporteDTO.M_MOTIVO);
        esErrorDeCampo(reportar(sesion, "{\"tipoObjetivo\":\"ESPACIO\",\"objetivoId\":" + espacio + "}"), "motivo",
                CrearReporteDTO.M_MOTIVO);
        esErrorDeCampo(reportar(sesion, "{\"tipoObjetivo\":\"ESPACIO\",\"objetivoId\":" + espacio
                + ",\"motivo\":[\"SPAM\"]}"), "motivo", CrearReporteDTO.M_MOTIVO);
        sinReporte();
    }

    // ── Cupo de 10 en 24 h (STRIDE D) ────────────────────────────────────

    private List<Long> espacios(int cantidad) {
        var otro = crearArrendador(CORREO_OTRO_ARRENDADOR);
        var ids = new ArrayList<Long>();
        for (var i = 0; i < cantidad; i++) {
            ids.add(espacioDe(otro));
        }
        return ids;
    }

    @Test
    @DisplayName("HU-27 R16, R17: con 9 en la ventana y uno de hace 24 h exactas, el décimo entra y el 11.º da 429")
    void cupoDeDiez() throws Exception {
        var ids = espacios(12);
        sembrarReporte(usuario, "ESPACIO", ids.get(0), "SPAM", reloj.instant().minus(Duration.ofHours(24)));
        for (var i = 1; i <= 9; i++) {
            sembrarReporte(usuario, "ESPACIO", ids.get(i), "SPAM",
                    reloj.instant().minus(Duration.ofHours(24)).plusSeconds(1));
        }
        var sesion = token(CORREO_USUARIO);

        reportar(sesion, "ESPACIO", ids.get(10), "SPAM").andExpect(status().isCreated());
        reportar(sesion, "ESPACIO", ids.get(11), "SPAM").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS));

        assertThat(filas("reporte")).isEqualTo(11);
        assertThat(auditorias("REPORTE_CREADO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-27 R19: con el cupo agotado, un contenido inexistente o propio también da 429")
    void cupoAntesQueElContenido() throws Exception {
        var ids = espacios(10);
        for (var id : ids) {
            sembrarReporte(arrendador, "ESPACIO", id, "SPAM", reloj.instant().minusSeconds(60));
        }
        var sesion = token(CORREO_ARRENDADOR);

        reportar(sesion, "ESPACIO", espacio + 100000, "SPAM").andExpect(status().isTooManyRequests());
        reportar(sesion, "ESPACIO", espacio, "SPAM").andExpect(status().isTooManyRequests());
        reportar(sesion, "ESPACIO", ids.get(0), "SPAM").andExpect(status().isTooManyRequests());
        assertThat(filas("reporte")).isEqualTo(10);
    }

    @Test
    @DisplayName("HU-27 R18: once reportes simultáneos con 9 previos: solo uno entra y los demás reciben 429")
    void onceSimultaneos() throws Exception {
        var ids = espacios(20);
        for (var i = 0; i < 9; i++) {
            sembrarReporte(usuario, "ESPACIO", ids.get(i), "SPAM", reloj.instant().minusSeconds(3600));
        }
        var sesion = token(CORREO_USUARIO);
        List<Callable<MvcResult>> solicitudes = new ArrayList<>();
        for (var i = 9; i < 20; i++) {
            var objetivo = ids.get(i);
            solicitudes.add(() -> reportar(sesion, "ESPACIO", objetivo, "SPAM").andReturn());
        }

        var codigos = aLaVez(solicitudes).stream().map(r -> r.getResponse().getStatus()).toList();

        assertThat(codigos.stream().filter(codigo -> codigo == 201).count()).isEqualTo(1);
        assertThat(codigos.stream().filter(codigo -> codigo == 429).count()).isEqualTo(10);
        assertThat(filas("reporte")).isEqualTo(10);
    }

    // ── Auditoría (STRIDE R) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-27 R50: audita REPORTE_CREADO con el reportante, la IP y tipo, objetivo y motivo")
    void auditaLaCreacion() throws Exception {
        var id = numero(cuerpo(reportar(token(CORREO_USUARIO), "ESPACIO", espacio, "INFORMACION_FALSA")
                .andExpect(status().isCreated())).get("id"));

        var auditoria = auditorias("REPORTE_CREADO");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.getFirst().get("entidad")).isEqualTo("reporte");
        assertThat(numero(auditoria.getFirst().get("entidad_id"))).isEqualTo(id);
        assertThat(numero(auditoria.getFirst().get("usuario_id"))).isEqualTo(usuario);
        assertThat(auditoria.getFirst().get("ip")).isEqualTo(ip);
        assertThat(auditoria.getFirst().get("detalle"))
                .isEqualTo("tipo=ESPACIO;objetivoId=" + espacio + ";motivo=INFORMACION_FALSA");
    }
}
