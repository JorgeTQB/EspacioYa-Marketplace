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
import java.util.ArrayList;
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
import pe.edu.ulima.espacioya.administracion.service.ReclamoService;
import pe.edu.ulima.espacioya.administracion.service.correo.CorreoReclamoAbierto;

/**
 * HU-26 (abrir, hilo y consulta de las partes) por la API contra PostgreSQL real. El reloj está fijo en el miércoles
 * 2026-11-04 a las 10:00 de Lima.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReclamoControllerIT extends ReclamosSoporte {

    private static final String NO_RECLAMABLE = ReclamoService.MENSAJE_NO_RECLAMABLE;
    private static final String PLAZO_VENCIDO = ReclamoService.MENSAJE_PLAZO_VENCIDO;
    private static final String YA_RECLAMADA = ReclamoService.MENSAJE_YA_RECLAMADA;
    private static final String M_RESERVA = "Elige una reserva válida.";
    private static final String M_MOTIVO = "Elige un motivo de la lista.";
    private static final String M_DESCRIPCION = "Describe el problema en hasta 1000 caracteres.";
    private static final String M_TEXTO = "Escribe un mensaje de hasta 1000 caracteres.";
    private static final String M_PAGINA = "La página no es válida.";
    private static final int RONDAS = 3;

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

    private void sinReclamo() {
        assertThat(filas("reclamo")).isZero();
        assertThat(auditorias("RECLAMO_ABIERTO")).isEmpty();
    }

    private ResultActions reclamables(String token) throws Exception {
        return mvc.perform(con(token, get(RECLAMABLES)));
    }

    private ResultActions listar(String token, String consulta) throws Exception {
        return mvc.perform(con(token, get(URI.create(RECLAMOS + consulta))));
    }

    private ResultActions detalle(String token, Object id) throws Exception {
        return mvc.perform(con(token, get(URI.create(RECLAMOS + "/" + id))));
    }

    private ResultActions escribir(String token, Object id, String cuerpo) throws Exception {
        return mvc.perform(con(token, post(URI.create(RECLAMOS + "/" + id + "/mensajes")))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions escribir(String token, Object id) throws Exception {
        return escribir(token, id, "{\"texto\":\"Hola\"}");
    }

    @SuppressWarnings("unchecked")
    private List<Long> idsDe(ResultActions respuesta, String campo) throws Exception {
        var valores = (List<Object>) cuerpo(respuesta.andExpect(status().isOk())).get(campo);
        return valores.stream().map(v -> v instanceof Map<?, ?> fila ? numero(fila.get("id")) : numero(v)).toList();
    }

    // ── Abrir (CA1) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R1, R2: una reserva reclamable responde 201 con el detalle y guarda el reclamo ABIERTO")
    void abre() throws Exception {
        var reserva = reclamableConPago();

        var respuesta = abrir(token(CORREO_USUARIO), reserva).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(reserva))
                .andExpect(jsonPath("$.espacioNombre").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.fecha").value(hoy.minusDays(1).toString()))
                .andExpect(jsonPath("$.horaInicio").value(8))
                .andExpect(jsonPath("$.horaFin").value(9))
                .andExpect(jsonPath("$.motivo").value("ESPACIO_CERRADO"))
                .andExpect(jsonPath("$.descripcion").value("Llegamos y estaba cerrado"))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.abiertoEn").value("2026-11-04T15:00:00Z"))
                .andExpect(jsonPath("$.mensajes", hasSize(0)));

        assertThat(cuerpo(respuesta)).containsKeys("id", "reservaId", "espacioNombre", "fecha", "horaInicio",
                "horaFin", "motivo", "descripcion", "estado", "abiertoEn", "mensajes");
        var fila = reclamoDe(reserva);
        assertThat(fila.get("estado")).isEqualTo("ABIERTO");
        assertThat(fila.get("motivo")).isEqualTo("ESPACIO_CERRADO");
        assertThat(fila.get("resolucion")).isNull();
    }

    @Test
    @DisplayName("HU-26 R3: el usuario sale de la sesión y el arrendador del espacio; los campos extra se ignoran")
    void identidadDelServidor() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var reserva = reclamableConPago();

        abrir(token(CORREO_USUARIO), "{\"reservaId\":" + reserva + ",\"motivo\":\"OTRO\",\"descripcion\":\"x\","
                + "\"usuarioId\":" + otro + ",\"arrendadorId\":" + otro + ",\"estado\":\"RESUELTO_USUARIO\"}")
                .andExpect(status().isCreated());

        var fila = reclamoDe(reserva);
        assertThat(numero(fila.get("usuario_id"))).isEqualTo(usuario);
        assertThat(numero(fila.get("arrendador_id"))).isEqualTo(arrendador);
        assertThat(fila.get("estado")).isEqualTo("ABIERTO");
    }

    @Test
    @DisplayName("HU-26 R4: se reclama en el instante del inicio y durante la reserva")
    void enCursoYEnElInicio() throws Exception {
        var sesion = token(CORREO_USUARIO);

        abrir(sesion, confirmada(hoy, 10)).andExpect(status().isCreated());
        abrir(sesion, confirmada(hoy, 9)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-26 R4: un nanosegundo antes de fin + 48 h aún se reclama (fin hace 47 h)")
    void dentroDeLas48Horas() throws Exception {
        abrir(token(CORREO_USUARIO), confirmada(hoy.minusDays(2), 10)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-26 R5: pendiente, expirada y cancelada responden no reclamable")
    void estadosNoConfirmados() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var dia = hoy.minusDays(1);

        esRechazo(abrir(sesion, reserva(usuario, espacio, "PENDIENTE_PAGO", dia, 8)), NO_RECLAMABLE);
        esRechazo(abrir(sesion, reserva(usuario, espacio, "EXPIRADA", dia, 9)), NO_RECLAMABLE);
        esRechazo(abrir(sesion, reserva(usuario, espacio, "CANCELADA", dia, 10)), NO_RECLAMABLE);
        sinReclamo();
    }

    @Test
    @DisplayName("HU-26 R6: una confirmada que aún no empieza responde no reclamable")
    void sinEmpezar() throws Exception {
        esRechazo(abrir(token(CORREO_USUARIO), confirmada(hoy, 11)), NO_RECLAMABLE);
        sinReclamo();
    }

    @Test
    @DisplayName("HU-26 R7: en fin + 48 h exactos responde plazo vencido")
    void plazoVencido() throws Exception {
        esRechazo(abrir(token(CORREO_USUARIO), confirmada(hoy.minusDays(2), 9)), PLAZO_VENCIDO);
        sinReclamo();
    }

    @Test
    @DisplayName("HU-26 R9: una reserva ajena y una inexistente reciben el mismo 404")
    void ajenaEInexistente() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var ajena = reserva(otro, espacio, "CONFIRMADA", hoy.minusDays(1), 8);
        var sesion = token(CORREO_USUARIO);

        var deAjena = abrir(sesion, ajena);
        var deInexistente = abrir(sesion, ajena + 1000);

        esNoEncontrado(deAjena);
        esNoEncontrado(deInexistente);
        assertThat(cuerpo(deAjena)).isEqualTo(cuerpo(deInexistente));
        sinReclamo();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-26 R10: ARRENDADOR y ADMIN reciben 403 al abrir y en reclamables")
    void otroRol(String rol) throws Exception {
        var reserva = reclamableConPago();
        if ("ADMIN".equals(rol)) {
            crearAdmin();
        }
        var sesion = token("ADMIN".equals(rol) ? CORREO_ADMIN : CORREO_ARRENDADOR);

        abrir(sesion, reserva).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        reclamables(sesion).andExpect(status().isForbidden());
        sinReclamo();
    }

    @Test
    @DisplayName("HU-26 R11: sin sesión válida todas las rutas de reclamos responden 401")
    void sinSesion() throws Exception {
        var reserva = reclamableConPago();

        abrir(null, reserva).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        abrir("token-inventado", reserva).andExpect(status().isUnauthorized());
        reclamables(null).andExpect(status().isUnauthorized());
        listar(null, "").andExpect(status().isUnauthorized());
        detalle(null, 1).andExpect(status().isUnauthorized());
        escribir(null, 1).andExpect(status().isUnauthorized());
        mvc.perform(comoAdmin(null, get(URI.create(ADMIN_RECLAMOS + "?grupo=abiertos"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(comoAdmin("token-inventado", get(URI.create(ADMIN_RECLAMOS + "/1"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(comoAdmin(null, post(URI.create(ADMIN_RECLAMOS + "/1/resolucion")))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        sinReclamo();
    }

    // ── Uno por reserva ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R12: el segundo reclamo de una reserva responde ya reclamada, también si el primero se resolvió")
    void segundoReclamo() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = reclamableConPago();
        abrir(sesion, reserva).andExpect(status().isCreated());
        var resuelta = confirmada(hoy.minusDays(1), 9);
        resolverPorJdbc(sembrarReclamo(resuelta), "RESUELTO_ARRENDADOR", null, crearAdmin(),
                reloj.instant().plusSeconds(60));

        esRechazo(abrir(sesion, reserva), YA_RECLAMADA);
        esRechazo(abrir(sesion, resuelta), YA_RECLAMADA);
        assertThat(filas("reclamo")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-26 R13, R14: dos envíos simultáneos dejan un 201, un 400 ya reclamada y una sola fila")
    void dosEnviosALaVez() throws Exception {
        var sesion = token(CORREO_USUARIO);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var reserva = confirmada(hoy.minusDays(1), 8 + ronda);

            var respuestas = aLaVez(List.of(() -> abrir(sesion, reserva).andReturn(),
                    () -> abrir(sesion, reserva).andReturn()));

            assertThat(respuestas).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 400);
            var rechazo = respuestas.stream().filter(r -> r.getResponse().getStatus() == 400).findFirst().orElseThrow();
            assertThat(mapa(rechazo.getResponse().getContentAsString()).get("mensaje")).isEqualTo(YA_RECLAMADA);
            assertThat(filas("reclamo")).isEqualTo(ronda + 1);
            assertThat(auditorias("RECLAMO_ABIERTO")).hasSize(ronda + 1);
        }
    }

    // ── Validación ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"", "\"reservaId\":\"90\",", "\"reservaId\":1.5,", "\"reservaId\":0,",
            "\"reservaId\":9223372036854775808,"})
    @DisplayName("HU-26 R15: un reservaId inválido responde 400 en su campo")
    void reservaInvalida(String campo) throws Exception {
        esErrorDeCampo(abrir(token(CORREO_USUARIO), "{" + campo + "\"motivo\":\"OTRO\",\"descripcion\":\"x\"}"),
                "reservaId", M_RESERVA);
        sinReclamo();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"motivo\":\"SUCIO\",", "\"motivo\":3,"})
    @DisplayName("HU-26 R16: un motivo fuera del catálogo responde 400 en su campo")
    void motivoInvalido(String campo) throws Exception {
        var cuerpo = "{\"reservaId\":" + reclamableConPago() + "," + campo + "\"descripcion\":\"x\"}";

        esErrorDeCampo(abrir(token(CORREO_USUARIO), cuerpo), "motivo", M_MOTIVO);
        sinReclamo();
    }

    @Test
    @DisplayName("HU-26 R17, R19: una descripción en blanco, de 1001 caracteres o solo etiquetas responde 400")
    void descripcionInvalida() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var plantilla = "{\"reservaId\":" + reclamableConPago() + ",\"motivo\":\"OTRO\",\"descripcion\":\"%s\"}";

        esErrorDeCampo(abrir(sesion, plantilla.formatted("  ")), "descripcion", M_DESCRIPCION);
        esErrorDeCampo(abrir(sesion, plantilla.formatted("a".repeat(1001))), "descripcion", M_DESCRIPCION);
        esErrorDeCampo(abrir(sesion, plantilla.formatted("<b></b>")), "descripcion", M_DESCRIPCION);
        sinReclamo();
    }

    @Test
    @DisplayName("HU-26 R19: una descripción que queda vacía de una reserva ajena sigue siendo 400, sin revelarla")
    void quedaVaciaSinLeerLaReserva() throws Exception {
        esErrorDeCampo(abrir(token(CORREO_USUARIO), "{\"reservaId\":999999,\"motivo\":\"OTRO\","
                + "\"descripcion\":\"<i></i>\"}"), "descripcion", M_DESCRIPCION);
    }

    @Test
    @DisplayName("HU-26 R18: la descripción se guarda sin ningún < ni >")
    void guardaSanitizada() throws Exception {
        var cuerpo = "{\"reservaId\":" + reclamableConPago() + ",\"motivo\":\"OTRO\","
                + "\"descripcion\":\"Cerra\\u0000do <script>alert(1)</script> hoy\"}";

        abrir(token(CORREO_USUARIO), cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.descripcion").value("Cerrado alert(1) hoy"));

        assertThat(jdbc.queryForObject("SELECT descripcion FROM reclamo", String.class))
                .isEqualTo("Cerrado alert(1) hoy").doesNotContain("<", ">");
    }

    // ── Correo y auditoría ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R22: al confirmarse, el arrendador recibe el aviso sin la descripción ni datos del usuario")
    void avisaAlArrendador() throws Exception {
        abrir(token(CORREO_USUARIO), reclamableConPago()).andExpect(status().isCreated());

        assertThat(correos.enviados()).singleElement().satisfies(correo -> {
            assertThat(correo.para()).isEqualTo(CORREO_ARRENDADOR);
            assertThat(correo.asunto()).isEqualTo(CorreoReclamoAbierto.ASUNTO);
            assertThat(correo.cuerpo()).contains(NOMBRE_ESPACIO, "Motivo: Espacio cerrado")
                    .doesNotContain("Llegamos y estaba cerrado", NOMBRE_USUARIO, CORREO_USUARIO, TELEFONO_USUARIO);
        });
    }

    @Test
    @DisplayName("HU-26 R22: un rechazo no envía el aviso")
    void rechazoSinCorreo() throws Exception {
        abrir(token(CORREO_USUARIO), confirmada(hoy, 11)).andExpect(status().isBadRequest());

        assertThat(correos.enviados()).isEmpty();
    }

    @Test
    @DisplayName("HU-26 R60: audita RECLAMO_ABIERTO con usuario, IP y detalle, sin la descripción")
    void auditaApertura() throws Exception {
        var reserva = reclamableConPago();

        var id = abrirPorLaApi(token(CORREO_USUARIO), reserva);

        assertThat(auditorias("RECLAMO_ABIERTO")).singleElement().satisfies(fila -> {
            assertThat(numero(fila.get("usuario_id"))).isEqualTo(usuario);
            assertThat(fila.get("entidad")).isEqualTo("reclamo");
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(id);
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle")).isEqualTo("reservaId=" + reserva + ";motivo=ESPACIO_CERRADO");
        });
    }

    // ── Reclamables ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R33, R41: reclamables lista solo las propias en ventana, sin reclamo, ascendentes y no-store")
    void reclamablesDelUsuario() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var ayer = confirmada(hoy.minusDays(1), 8);
        var enCurso = confirmada(hoy, 9);
        var conReclamo = confirmada(hoy.minusDays(1), 9);
        sembrarReclamo(conReclamo);
        confirmada(hoy, 11);
        confirmada(hoy.minusDays(2), 9);
        reserva(usuario, espacio, "CANCELADA", hoy.minusDays(1), 10);
        reserva(otro, espacio, "CONFIRMADA", hoy.minusDays(1), 11);

        var respuesta = reclamables(token(CORREO_USUARIO))
                .andExpect(header().string("Cache-Control", "no-store"));

        assertThat(idsDe(respuesta, "reservaIds")).containsExactly(ayer, enCurso);
    }

    // ── Hilo de mensajes ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R23, R32: usuario y arrendador escriben en el hilo y lo leen en orden con su papel")
    void hiloDeLasDosPartes() throws Exception {
        var sesionUsuario = token(CORREO_USUARIO);
        var id = abrirPorLaApi(sesionUsuario, reclamableConPago());
        var sesionArrendador = token(CORREO_ARRENDADOR);

        escribir(sesionUsuario, id, "{\"texto\":\"Sigue <b>cerrado</b>\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("USUARIO"))
                .andExpect(jsonPath("$.texto").value("Sigue cerrado"))
                .andExpect(jsonPath("$.fecha").value("2026-11-04T15:00:00Z"));
        escribir(sesionArrendador, id, "{\"texto\":\"Lo revisamos\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("ARRENDADOR"));

        detalle(sesionArrendador, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.papel").value("ARRENDADOR"))
                .andExpect(jsonPath("$.mensajes[0].texto").value("Sigue cerrado"))
                .andExpect(jsonPath("$.mensajes[0].papel").value("USUARIO"))
                .andExpect(jsonPath("$.mensajes[1].texto").value("Lo revisamos"))
                .andExpect(jsonPath("$.mensajesRestantes").value(9))
                .andExpect(jsonPath("$.puedeEscribir").value(true))
                .andExpect(jsonPath("$.aFavorDe").doesNotExist())
                .andExpect(jsonPath("$.montoReembolso").doesNotExist());
        var filas = jdbc.queryForList("SELECT * FROM mensaje_reclamo ORDER BY id");
        assertThat(filas).extracting(f -> numero(f.get("autor_id"))).containsExactly(usuario, arrendador);
        assertThat(filas).extracting(f -> f.get("papel")).containsExactly("USUARIO", "ARRENDADOR");
    }

    @Test
    @DisplayName("HU-26 R32: el detalle del arrendador no lleva montos ni datos personales del usuario")
    void detalleDelArrendadorSinDatosDelUsuario() throws Exception {
        var reserva = reclamableConPago();
        var id = sembrarReclamo(reserva);
        resolverPorJdbc(id, "RESUELTO_USUARIO", "100.00", crearAdmin(), reloj.instant().plusSeconds(60));

        var arrendadorVe = detalle(token(CORREO_ARRENDADOR), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.aFavorDe").value("USUARIO"))
                .andExpect(jsonPath("$.resolucion").value("Resuelto"))
                .andExpect(jsonPath("$.puedeEscribir").value(false));
        var usuarioVe = detalle(token(CORREO_USUARIO), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.montoReembolso").value(100.0));

        assertThat(cuerpo(arrendadorVe).get("montoReembolso")).isNull();
        assertThat(arrendadorVe.andReturn().getResponse().getContentAsString())
                .doesNotContain(NOMBRE_USUARIO, CORREO_USUARIO, TELEFONO_USUARIO, MONTO, "100.0");
        assertThat(cuerpo(usuarioVe).get("papel")).isEqualTo("USUARIO");
    }

    @Test
    @DisplayName("HU-26 R24: un reclamo resuelto responde reclamo cerrado y no guarda el mensaje")
    void reclamoCerrado() throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        resolverPorJdbc(id, "RESUELTO_ARRENDADOR", null, crearAdmin(), reloj.instant().plusSeconds(60));

        esRechazo(escribir(token(CORREO_USUARIO), id), ReclamoService.MENSAJE_RECLAMO_CERRADO);
        assertThat(filas("mensaje_reclamo")).isZero();
    }

    @Test
    @DisplayName("HU-26 R25: un tercero, un id inexistente y uno mal escrito reciben el mismo 404")
    void terceroOIdMalo() throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        crearUsuario(CORREO_OTRO);
        var tercero = token(CORREO_OTRO);
        var sesion = token(CORREO_USUARIO);

        esNoEncontrado(detalle(tercero, id));
        esNoEncontrado(escribir(tercero, id));
        esNoEncontrado(detalle(sesion, id + 1000));
        esNoEncontrado(escribir(sesion, id + 1000));
        esNoEncontrado(detalle(sesion, "abc"));
        esNoEncontrado(detalle(sesion, "0"));
        esNoEncontrado(detalle(sesion, "99999999999999999999"));
        esNoEncontrado(escribir(sesion, "abc"));
        assertThat(filas("mensaje_reclamo")).isZero();
    }

    @Test
    @DisplayName("HU-26 R26: el admin recibe 403 en la lista, el detalle y los mensajes de las partes")
    void adminNoEsParte() throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        crearAdmin();
        var admin = token(CORREO_ADMIN);

        mvc.perform(comoAdmin(admin, get(RECLAMOS))).andExpect(status().isForbidden());
        mvc.perform(comoAdmin(admin, get(URI.create(RECLAMOS + "/" + id)))).andExpect(status().isForbidden());
        mvc.perform(comoAdmin(admin, post(URI.create(RECLAMOS + "/" + id + "/mensajes")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola\"}"))
                .andExpect(status().isForbidden());
        assertThat(filas("mensaje_reclamo")).isZero();
    }

    @Test
    @DisplayName("HU-26 R27: el 11.º mensaje de una parte responde límite de mensajes; la otra parte sigue")
    void undecimoMensaje() throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        var sesion = token(CORREO_USUARIO);
        for (var i = 0; i < 10; i++) {
            escribir(sesion, id).andExpect(status().isCreated());
        }

        esRechazo(escribir(sesion, id), ReclamoService.MENSAJE_LIMITE_MENSAJES);
        escribir(token(CORREO_ARRENDADOR), id).andExpect(status().isCreated());
        detalle(sesion, id).andExpect(jsonPath("$.mensajesRestantes").value(0))
                .andExpect(jsonPath("$.puedeEscribir").value(false));
        assertThat(filas("mensaje_reclamo")).isEqualTo(11);
    }

    @Test
    @DisplayName("HU-26 R28: un texto en blanco, de otro tipo, de 1001 caracteres o solo etiquetas responde 400")
    void textoInvalido() throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        var sesion = token(CORREO_USUARIO);

        esErrorDeCampo(escribir(sesion, id, "{\"texto\":\" \"}"), "texto", M_TEXTO);
        esErrorDeCampo(escribir(sesion, id, "{\"texto\":5}"), "texto", M_TEXTO);
        esErrorDeCampo(escribir(sesion, id, "{\"texto\":\"" + "a".repeat(1001) + "\"}"), "texto", M_TEXTO);
        esErrorDeCampo(escribir(sesion, id, "{\"texto\":\"<p></p>\"}"), "texto", M_TEXTO);
        assertThat(filas("mensaje_reclamo")).isZero();
    }

    @Test
    @DisplayName("HU-26 R29: con 9 mensajes, dos envíos simultáneos dejan un 201 y un 400 límite de mensajes")
    void limiteConcurrente() throws Exception {
        var sesion = token(CORREO_USUARIO);
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var id = sembrarReclamo(confirmada(hoy.minusDays(1), 8 + ronda));
            for (var i = 0; i < 9; i++) {
                sembrarMensaje(id, usuario, "USUARIO", "m" + i, reloj.instant().minusSeconds(60));
            }

            var respuestas = aLaVez(List.of(() -> escribir(sesion, id).andReturn(),
                    () -> escribir(sesion, id).andReturn()));

            assertThat(respuestas).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 400);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje_reclamo WHERE reclamo_id = ?",
                    Integer.class, id)).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("HU-26 R30: no hay rutas para editar ni borrar reclamos o mensajes (405)")
    void sinEdicion() throws Exception {
        var id = sembrarReclamo(reclamableConPago());
        var sesion = token(CORREO_USUARIO);

        mvc.perform(con(sesion, delete(URI.create(RECLAMOS + "/" + id)))).andExpect(status().isMethodNotAllowed());
        mvc.perform(con(sesion, put(URI.create(RECLAMOS + "/" + id))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"x\"}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(con(sesion, delete(URI.create(RECLAMOS + "/" + id + "/mensajes"))))
                .andExpect(status().isMethodNotAllowed());
        assertThat(filas("reclamo")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-26 R61: audita RECLAMO_MENSAJE con autor, IP y papel, sin el texto")
    void auditaMensaje() throws Exception {
        var id = sembrarReclamo(reclamableConPago());

        var mensaje = numero(cuerpo(escribir(token(CORREO_ARRENDADOR), id, "{\"texto\":\"Texto privado\"}")
                .andExpect(status().isCreated())).get("id"));

        assertThat(auditorias("RECLAMO_MENSAJE")).singleElement().satisfies(fila -> {
            assertThat(numero(fila.get("usuario_id"))).isEqualTo(arrendador);
            assertThat(numero(fila.get("entidad_id"))).isEqualTo(id);
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle")).isEqualTo("mensajeId=" + mensaje + ";papel=ARRENDADOR");
        });
    }

    // ── Listas ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-26 R31, R41: cada parte lista solo lo suyo, de a 10, más nuevos primero, con no-store")
    void listasPorParte() throws Exception {
        var ids = new ArrayList<Long>();
        for (var hora = 0; hora < 11; hora++) {
            ids.add(sembrarReclamo(confirmada(hoy.minusDays(1), hora), usuario, arrendador,
                    reloj.instant().minus(Duration.ofMinutes(60 - hora))));
        }
        var otroArrendador = crearArrendador(CORREO_OTRO_ARRENDADOR);
        var ajeno = sembrarReclamo(reserva(usuario, espacioDe(otroArrendador), "CONFIRMADA", hoy.minusDays(1), 12),
                usuario, otroArrendador, reloj.instant().minus(Duration.ofHours(5)));
        var enOrden = ids.reversed();
        var sesionUsuario = token(CORREO_USUARIO);
        var sesionArrendador = token(CORREO_ARRENDADOR);

        var primera = listar(sesionUsuario, "").andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pagina").value(1)).andExpect(jsonPath("$.hayMas").value(true))
                .andExpect(jsonPath("$.reclamos[0].espacioNombre").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.reclamos[0].estado").value("ABIERTO"));
        var segunda = listar(sesionUsuario, "?pagina=2").andExpect(jsonPath("$.hayMas").value(false));

        assertThat(idsDe(primera, "reclamos")).containsExactlyElementsOf(enOrden.subList(0, 10));
        assertThat(idsDe(segunda, "reclamos")).containsExactly(enOrden.get(10), ajeno);
        var delArrendador = new ArrayList<>(idsDe(listar(sesionArrendador, ""), "reclamos"));
        delArrendador.addAll(idsDe(listar(sesionArrendador, "?pagina=2"), "reclamos"));
        assertThat(delArrendador).containsExactlyElementsOf(enOrden).doesNotContain(ajeno);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?pagina=", "?pagina=0", "?pagina=-1", "?pagina=abc", "?pagina=2147483648"})
    @DisplayName("HU-26 R34: una página inválida responde 400 en su campo")
    void paginaInvalida(String consulta) throws Exception {
        esErrorDeCampo(listar(token(CORREO_USUARIO), consulta), "pagina", M_PAGINA);
    }
}
