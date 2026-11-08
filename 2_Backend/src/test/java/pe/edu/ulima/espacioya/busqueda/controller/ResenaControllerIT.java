package pe.edu.ulima.espacioya.busqueda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU-24 por la API contra PostgreSQL real. El reloj está fijo en el miércoles 2026-11-04 a las 10:00 en punto de Lima y
 * ningún test lo mueve: con eso, una reserva de 9 a 10 termina exactamente "ahora". Cada test usa su propia IP
 * (10.24.x.x). Las reservas y las reseñas se siembran por JDBC cuando el test no trata de crearlas.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ResenaControllerIT.Dobles.class})
class ResenaControllerIT {

    private static final String RESENAS = "/api/resenas";
    private static final String PENDIENTES = "/api/resenas/pendientes";
    private static final String CONTRASENA = "Segura123!";
    private static final String CORREO_USUARIO = "carla.resena@correo.pe";
    private static final String CORREO_OTRO = "beto.resena@correo.pe";
    private static final String CORREO_ARRENDADOR = "ana.resena@correo.pe";
    private static final String CORREO_ADMIN = "admin.resena@espacioya.pe";
    private static final String IP_ADMIN = "127.0.0.1";
    private static final String NOMBRE_USUARIO = "Carla Reseñadora";

    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String NO_RESENABLE = "Solo puedes reseñar reservas confirmadas que ya pasaron.";
    private static final String PLAZO_VENCIDO = "El plazo de 14 días para reseñar esta reserva ya terminó.";
    private static final String YA_RESENADA = "Ya reseñaste esta reserva.";
    private static final String M_RESERVA = "Elige una reserva válida.";
    private static final String M_CALIFICACION = "Elige una calificación de 1 a 5.";
    private static final String M_COMENTARIO = "Escribe un comentario de hasta 500 caracteres.";
    private static final String M_PAGINA = "La página no es válida.";

    private static final int RONDAS = 5;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static String hash;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private CifradorCampos cifrador;
    @Autowired
    private JsonMapper json;

    private String ip;
    private long arrendador;
    private long usuario;
    private long espacio;
    private LocalDate hoy;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-11-04T15:00:00Z"));
        }
    }

    @BeforeEach
    void prepararBase() {
        limpiar();
        if (hash == null) {
            hash = encoder.encode(CONTRASENA);
        }
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.24." + (n / 250) + "." + (n % 250 + 1);
        hoy = ZonaHoraria.hoy(reloj);
        var cuenta = usuarios.save(Usuario.registrarArrendador(CORREO_ARRENDADOR, hash, "Ana Reservada", "999111222",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(cuenta.getId(), TipoDocumento.RUC, "20100070970", "1".repeat(64),
                "Inversiones Ocultas SAC", "Jr. Fiscal Escondido 777"));
        arrendador = cuenta.getId();
        usuario = crearUsuario(CORREO_USUARIO);
        jdbc.update("UPDATE usuario SET nombre = ? WHERE id = ?", NOMBRE_USUARIO, usuario);
        espacio = espacioEn("PUBLICADO");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM desafio_mfa");
        jdbc.update("DELETE FROM codigo_respaldo");
        jdbc.update("DELETE FROM mfa");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private long crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hash, "2026-10", reloj.instant())).getId();
    }

    private void crearAdmin() {
        var id = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hash, "2026-10", reloj.instant())).getId();
        MfaDePrueba.activar(jdbc, cifrador, id, reloj.instant());
    }

    private long espacioEn(String estado) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Cancha Olivos', "
                        + "'CANCHA', 'Av. Universitaria 1234', 'LOS_OLIVOS', 22, 80.00, 'Cancha de grass sintético.', "
                        + "?, now(), now()) RETURNING id", Long.class, arrendador, estado);
    }

    /** Una fila de reserva con un bloque por cada hora de {@code dia}. */
    private long reserva(long dueno, long enEspacio, String estado, Instant expiraEn, LocalDate dia, int... horas) {
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.00, 80.00, ?, ?) RETURNING id",
                Long.class, dueno, enEspacio, estado, Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))),
                Timestamp.from(expiraEn));
        for (var hora : horas) {
            bloque(id, enEspacio, dia, hora, true);
        }
        return id;
    }

    private void bloque(long reserva, long enEspacio, LocalDate dia, int hora, boolean activo) {
        jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                + "VALUES (?, ?, ?, ?, ?)", reserva, enEspacio, Date.valueOf(dia), hora, activo);
    }

    private Instant yaPagada() {
        return reloj.instant().minus(Duration.ofDays(30));
    }

    private long confirmada(long dueno, long enEspacio, LocalDate dia, int... horas) {
        return reserva(dueno, enEspacio, "CONFIRMADA", yaPagada(), dia, horas);
    }

    /** Terminó hace {@code dias} días: reseñable si son menos de 14. */
    private long pasada(int dias, int hora) {
        return confirmada(usuario, espacio, hoy.minusDays(dias), hora);
    }

    private long sembrarResena(long reserva, long autor, long enEspacio, int calificacion, String estado,
                               Instant creadoEn) {
        return jdbc.queryForObject("INSERT INTO resena (reserva_id, usuario_id, espacio_id, calificacion, comentario, "
                        + "estado, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, reserva, autor,
                enEspacio, calificacion, "Comentario " + calificacion, estado, Timestamp.from(creadoEn));
    }

    private long sembrarResena(long reserva, int calificacion, String estado) {
        return sembrarResena(reserva, usuario, espacio, calificacion, estado, reloj.instant());
    }

    private int filasDeResena() {
        return jdbc.queryForObject("SELECT count(*) FROM resena", Integer.class);
    }

    private List<Map<String, Object>> auditorias() {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = 'RESENA_CREADA' ORDER BY id");
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud, String origen) {
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
        return solicitud;
    }

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T con(String token, T solicitud) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return desde(solicitud, ip);
    }

    private String token(String correo) throws Exception {
        if (CORREO_ADMIN.equals(correo)) {
            return MfaDePrueba.iniciarSesion(mvc, jdbc, correo, CONTRASENA, IP_ADMIN, reloj.instant());
        }
        var cuerpo = mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    private ResultActions enviar(String token, String cuerpo) throws Exception {
        return mvc.perform(con(token, post(RESENAS)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions resenar(String token, long reserva) throws Exception {
        return enviar(token, "{\"reservaId\":" + reserva + ",\"calificacion\":4,\"comentario\":\"Buena cancha\"}");
    }

    private ResultActions pendientes(String token) throws Exception {
        return mvc.perform(con(token, get(PENDIENTES)));
    }

    private ResultActions ficha(long id) throws Exception {
        return mvc.perform(con(null, get("/api/espacios/" + id)));
    }

    private ResultActions lista(Object id, String consulta) throws Exception {
        return mvc.perform(con(null, get(URI.create("/api/espacios/" + id + "/resenas" + consulta))));
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    private Map<String, Object> cuerpo(ResultActions respuesta) throws Exception {
        return mapa(respuesta.andReturn().getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resenasDe(ResultActions respuesta) throws Exception {
        return (List<Map<String, Object>>) cuerpo(respuesta.andExpect(status().isOk())).get("resenas");
    }

    private List<Long> idsDe(ResultActions respuesta) throws Exception {
        return resenasDe(respuesta).stream().map(resena -> ((Number) resena.get("id")).longValue()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> pendientesDe(ResultActions respuesta) throws Exception {
        var ids = (List<Number>) cuerpo(respuesta.andExpect(status().isOk())).get("reservaIds");
        return ids.stream().map(Number::longValue).toList();
    }

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

    private void sinResena() {
        assertThat(filasDeResena()).isZero();
        assertThat(auditorias()).isEmpty();
    }

    // ── Crear (CA1) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R1, R2: una reserva reseñable responde 201 con exactamente sus cinco campos")
    void creaYRespondeCincoCampos() throws Exception {
        var reserva = pasada(3, 8);

        var respuesta = resenar(token(CORREO_USUARIO), reserva).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value(reserva))
                .andExpect(jsonPath("$.calificacion").value(4))
                .andExpect(jsonPath("$.comentario").value("Buena cancha"))
                .andExpect(jsonPath("$.fecha").value(hoy.toString()));

        assertThat(cuerpo(respuesta)).containsOnlyKeys("id", "reservaId", "calificacion", "comentario", "fecha");
    }

    @Test
    @DisplayName("HU-24 R1, R3: guarda VISIBLE con el usuario de la sesión y el espacio de la reserva, no del cuerpo")
    void identidadDelServidor() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var otroEspacio = espacioEn("PUBLICADO");
        var reserva = pasada(3, 8);

        enviar(token(CORREO_USUARIO), "{\"reservaId\":" + reserva + ",\"calificacion\":5,\"comentario\":\"Bien\","
                + "\"usuarioId\":" + otro + ",\"espacioId\":" + otroEspacio + ",\"estado\":\"OCULTA\"}")
                .andExpect(status().isCreated());

        var fila = jdbc.queryForMap("SELECT * FROM resena");
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario);
        assertThat(((Number) fila.get("espacio_id")).longValue()).isEqualTo(espacio);
        assertThat(((Number) fila.get("reserva_id")).longValue()).isEqualTo(reserva);
        assertThat(fila.get("estado")).isEqualTo("VISIBLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAUSADO", "BAJA", "OCULTO"})
    @DisplayName("HU-24 R1: también se reseña si el espacio ya no es visible")
    void espacioNoVisible(String estado) throws Exception {
        var reserva = pasada(3, 8);
        jdbc.update("UPDATE espacio SET estado = ? WHERE id = ?", estado, espacio);

        resenar(token(CORREO_USUARIO), reserva).andExpect(status().isCreated());

        assertThat(filasDeResena()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-24 R4: el fin cuenta también los bloques inactivos")
    void finConBloquesInactivos() throws Exception {
        // Activo de 8 a 9 (ya pasó) e inactivo de 20 a 21: el fin son las 21:00, así que aún no se puede reseñar.
        var reserva = confirmada(usuario, espacio, hoy, 8);
        bloque(reserva, espacio, hoy, 20, false);

        esRechazo(resenar(token(CORREO_USUARIO), reserva), NO_RESENABLE);
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R5: una reserva de 9 a 10 se reseña justo a las 10:00")
    void enElInstanteDelFin() throws Exception {
        var reserva = confirmada(usuario, espacio, hoy, 9);

        resenar(token(CORREO_USUARIO), reserva).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-24 R5: 1 hora antes de fin + 14 días todavía se reseña")
    void dentroDelPlazo() throws Exception {
        var reserva = confirmada(usuario, espacio, hoy.minusDays(14), 10);

        resenar(token(CORREO_USUARIO), reserva).andExpect(status().isCreated());
    }

    // ── Rechazos (CA2) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R6: pendiente, expirada, vencida sin expirar y cancelada responden no reseñable")
    void estadosNoConfirmados() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var vigente = reloj.instant().plus(Duration.ofMinutes(10));
        var dia = hoy.minusDays(2);

        esRechazo(resenar(sesion, reserva(usuario, espacio, "PENDIENTE_PAGO", vigente, dia, 8)), NO_RESENABLE);
        esRechazo(resenar(sesion, reserva(usuario, espacio, "PENDIENTE_PAGO", yaPagada(), dia, 9)), NO_RESENABLE);
        esRechazo(resenar(sesion, reserva(usuario, espacio, "EXPIRADA", yaPagada(), dia, 10)), NO_RESENABLE);
        esRechazo(resenar(sesion, reserva(usuario, espacio, "CANCELADA", yaPagada(), dia, 11)), NO_RESENABLE);
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R7: una confirmada futura o en curso responde no reseñable")
    void sinTerminar() throws Exception {
        var sesion = token(CORREO_USUARIO);

        esRechazo(resenar(sesion, confirmada(usuario, espacio, hoy.plusDays(1), 10)), NO_RESENABLE);
        esRechazo(resenar(sesion, confirmada(usuario, espacio, hoy, 10)), NO_RESENABLE);
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R8: en fin + 14 días exactos responde plazo vencido")
    void plazoVencido() throws Exception {
        var reserva = confirmada(usuario, espacio, hoy.minusDays(14), 9);

        esRechazo(resenar(token(CORREO_USUARIO), reserva), PLAZO_VENCIDO);
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R9: una reserva ajena y una inexistente reciben la misma respuesta 404")
    void ajenaEInexistente() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var ajena = confirmada(otro, espacio, hoy.minusDays(2), 8);
        var sesion = token(CORREO_USUARIO);

        var deAjena = resenar(sesion, ajena);
        var deInexistente = resenar(sesion, ajena + 1000);

        esNoEncontrado(deAjena);
        esNoEncontrado(deInexistente);
        assertThat(cuerpo(deAjena)).isEqualTo(cuerpo(deInexistente));
        sinResena();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-24 R10: ARRENDADOR y ADMIN reciben 403 aunque la reserva exista")
    void otroRol(String rol) throws Exception {
        var reserva = pasada(3, 8);
        if ("ADMIN".equals(rol)) {
            crearAdmin();
        }
        var sesion = token("ADMIN".equals(rol) ? CORREO_ADMIN : CORREO_ARRENDADOR);

        resenar(sesion, reserva).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R11: sin sesión válida, crear y pendientes responden 401")
    void sinSesion() throws Exception {
        var reserva = pasada(3, 8);

        resenar(null, reserva).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        resenar("token-inventado", reserva).andExpect(status().isUnauthorized());
        pendientes(null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        pendientes("token-inventado").andExpect(status().isUnauthorized());
        sinResena();
    }

    // ── Una por reserva (CA3) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R12: la segunda reseña de una reserva responde ya reseñada y no crea otra")
    void dobleResena() throws Exception {
        var reserva = pasada(3, 8);
        var sesion = token(CORREO_USUARIO);
        resenar(sesion, reserva).andExpect(status().isCreated());

        esRechazo(resenar(sesion, reserva), YA_RESENADA);

        assertThat(filasDeResena()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-24 R12: una reseña oculta o con el plazo ya vencido también cuenta como ya reseñada")
    void yaResenadaOcultaOVencida() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var oculta = pasada(3, 8);
        sembrarResena(oculta, 2, "OCULTA");
        var vencida = pasada(30, 8);
        sembrarResena(vencida, 4, "VISIBLE");

        esRechazo(resenar(sesion, oculta), YA_RESENADA);
        esRechazo(resenar(sesion, vencida), YA_RESENADA);
    }

    @Test
    @DisplayName("HU-24 R14: dos envíos simultáneos dejan un 201, un 400 ya reseñada y una sola fila")
    void dosEnviosALaVez() throws Exception {
        var sesion = token(CORREO_USUARIO);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var reserva = pasada(ronda + 1, 8);

            var respuestas = aLaVez(List.of(() -> resenar(sesion, reserva).andReturn(),
                    () -> resenar(sesion, reserva).andReturn()));

            assertThat(respuestas).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 400);
            var rechazo = respuestas.stream().filter(r -> r.getResponse().getStatus() == 400).findFirst().orElseThrow();
            assertThat(mapa(rechazo.getResponse().getContentAsString()).get("mensaje")).isEqualTo(YA_RESENADA);
            assertThat(filasDeResena()).isEqualTo(ronda + 1);
            assertThat(auditorias()).hasSize(ronda + 1);
        }
    }

    private List<MvcResult> aLaVez(List<Callable<MvcResult>> solicitudes) throws Exception {
        var salida = new CountDownLatch(1);
        var respuestas = new ArrayList<MvcResult>();
        try (var hilos = Executors.newFixedThreadPool(solicitudes.size())) {
            var pendientes = solicitudes.stream().map(solicitud -> hilos.submit(() -> {
                salida.await();
                return solicitud.call();
            })).toList();
            salida.countDown();
            for (var pendiente : pendientes) {
                respuestas.add(pendiente.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }
        }
        return respuestas;
    }

    // ── Validación y sanitización ────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"", "\"reservaId\":null,", "\"reservaId\":\"90\",", "\"reservaId\":1.5,",
            "\"reservaId\":0,", "\"reservaId\":-1,", "\"reservaId\":9223372036854775808,"})
    @DisplayName("HU-24 R15: un reservaId inválido responde 400 en su campo")
    void reservaInvalida(String campo) throws Exception {
        var cuerpo = "{" + campo + "\"calificacion\":4,\"comentario\":\"Bien\"}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "reservaId", M_RESERVA);
        sinResena();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"calificacion\":null,", "\"calificacion\":\"4\",", "\"calificacion\":4.5,",
            "\"calificacion\":0,", "\"calificacion\":6,"})
    @DisplayName("HU-24 R16: una calificación inválida responde 400 en su campo")
    void calificacionInvalida(String campo) throws Exception {
        var cuerpo = "{\"reservaId\":" + pasada(3, 8) + "," + campo + "\"comentario\":\"Bien\"}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "calificacion", M_CALIFICACION);
        sinResena();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"calificacion\":4", "\"calificacion\":4,\"comentario\":null",
            "\"calificacion\":4,\"comentario\":123", "\"calificacion\":4,\"comentario\":\"   \""})
    @DisplayName("HU-24 R17: un comentario ausente, de otro tipo o en blanco responde 400 en su campo")
    void comentarioInvalido(String campos) throws Exception {
        var cuerpo = "{\"reservaId\":" + pasada(3, 8) + "," + campos + "}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "comentario", M_COMENTARIO);
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R17: 501 caracteres responden 400 y 500 se guardan")
    void largoDelComentario() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pasada(3, 8);
        var plantilla = "{\"reservaId\":" + reserva + ",\"calificacion\":4,\"comentario\":\"%s\"}";

        esErrorDeCampo(enviar(sesion, plantilla.formatted("a".repeat(501))), "comentario", M_COMENTARIO);
        enviar(sesion, plantilla.formatted("a".repeat(500))).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT char_length(comentario) FROM resena", Integer.class)).isEqualTo(500);
    }

    @Test
    @DisplayName("HU-24 R21: un <script> y un U+0000 se guardan limpios, sin ningún < ni >")
    void guardaSanitizado() throws Exception {
        var cuerpo = "{\"reservaId\":" + pasada(3, 8) + ",\"calificacion\":4,"
                + "\"comentario\":\"Bue\\u0000na <script>alert(1)</script> cancha\"}";

        enviar(token(CORREO_USUARIO), cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.comentario").value("Buena alert(1) cancha"));

        var guardado = jdbc.queryForObject("SELECT comentario FROM resena", String.class);
        assertThat(guardado).isEqualTo("Buena alert(1) cancha").doesNotContain("<", ">");
    }

    @Test
    @DisplayName("HU-24 R22: un comentario que queda vacío responde 400 en su campo")
    void quedaVacio() throws Exception {
        var cuerpo = "{\"reservaId\":" + pasada(3, 8) + ",\"calificacion\":4,\"comentario\":\"<b></b>\"}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "comentario", M_COMENTARIO);
        sinResena();
    }

    @Test
    @DisplayName("HU-24 R22: un comentario vacío de una reserva ajena sigue siendo 400, sin revelar si existe")
    void quedaVacioAntesDeLeerLaReserva() throws Exception {
        var cuerpo = "{\"reservaId\":999999,\"calificacion\":4,\"comentario\":\"<b></b>\"}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "comentario", M_COMENTARIO);
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R23: audita RESENA_CREADA con usuario, IP y detalle, sin el texto del comentario")
    void auditaLaCreacion() throws Exception {
        var reserva = pasada(3, 8);

        var id = ((Number) cuerpo(resenar(token(CORREO_USUARIO), reserva).andExpect(status().isCreated())).get("id"))
                .longValue();

        var filas = auditorias();
        assertThat(filas).hasSize(1);
        var fila = filas.getFirst();
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario);
        assertThat(fila.get("entidad")).isEqualTo("resena");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(id);
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(fila.get("detalle"))
                .isEqualTo("reservaId=" + reserva + ";espacioId=" + espacio + ";calificacion=4");
        assertThat(fila.get("detalle").toString()).doesNotContain("Buena cancha");
    }

    @Test
    @DisplayName("HU-24 R24: los rechazos no registran RESENA_CREADA")
    void rechazosSinAuditoria() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var futura = confirmada(usuario, espacio, hoy.plusDays(1), 10);

        resenar(sesion, futura).andExpect(status().isBadRequest());
        resenar(sesion, futura + 1000).andExpect(status().isNotFound());
        enviar(sesion, "{}").andExpect(status().isBadRequest());

        assertThat(auditorias()).isEmpty();
    }

    // ── Pendientes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R25: lista solo las reseñables propias, en orden ascendente")
    void pendientesDelUsuario() throws Exception {
        var otro = crearUsuario(CORREO_OTRO);
        var primera = pasada(3, 8);
        var justoAhora = confirmada(usuario, espacio, hoy, 9);
        var ultimaHora = confirmada(usuario, espacio, hoy.minusDays(14), 10);
        sembrarResena(pasada(4, 8), 5, "VISIBLE");
        sembrarResena(pasada(5, 8), 5, "OCULTA");
        confirmada(otro, espacio, hoy.minusDays(6), 8);
        confirmada(usuario, espacio, hoy.minusDays(14), 9);
        confirmada(usuario, espacio, hoy.plusDays(1), 10);
        confirmada(usuario, espacio, hoy, 10);
        reserva(usuario, espacio, "CANCELADA", yaPagada(), hoy.minusDays(7), 8);
        reserva(usuario, espacio, "EXPIRADA", yaPagada(), hoy.minusDays(7), 9);
        reserva(usuario, espacio, "PENDIENTE_PAGO", yaPagada(), hoy.minusDays(7), 10);

        assertThat(pendientesDe(pendientes(token(CORREO_USUARIO))))
                .containsExactly(primera, justoAhora, ultimaHora);
    }

    @Test
    @DisplayName("HU-24 R25: al reseñar, la reserva sale de los pendientes")
    void saleDePendientes() throws Exception {
        var reserva = pasada(3, 8);
        var sesion = token(CORREO_USUARIO);
        assertThat(pendientesDe(pendientes(sesion))).containsExactly(reserva);

        resenar(sesion, reserva).andExpect(status().isCreated());

        assertThat(pendientesDe(pendientes(sesion))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-24 R26: ARRENDADOR y ADMIN reciben 403 en pendientes")
    void pendientesConOtroRol(String rol) throws Exception {
        if ("ADMIN".equals(rol)) {
            crearAdmin();
        }
        var sesion = token("ADMIN".equals(rol) ? CORREO_ADMIN : CORREO_ARRENDADOR);

        pendientes(sesion).andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
    }

    // ── Ficha ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R27, R28: la ficha trae el promedio 4.3 de 4, 4, 4 y 5, y el total")
    void fichaConPromedio() throws Exception {
        sembrarResena(pasada(1, 8), 4, "VISIBLE");
        sembrarResena(pasada(2, 8), 4, "VISIBLE");
        sembrarResena(pasada(3, 8), 4, "VISIBLE");
        sembrarResena(pasada(4, 8), 5, "VISIBLE");

        ficha(espacio).andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").value(4.3))
                .andExpect(jsonPath("$.totalResenas").value(4));
    }

    @Test
    @DisplayName("HU-24 R29: sin reseñas, calificacionPromedio es nulo y totalResenas 0")
    void fichaSinResenas() throws Exception {
        var ficha = cuerpo(ficha(espacio).andExpect(status().isOk()));

        assertThat(ficha.get("calificacionPromedio")).isNull();
        assertThat(ficha.get("totalResenas")).isEqualTo(0);
    }

    @Test
    @DisplayName("HU-24 R30: 11 reseñas salen de a 10, de la más nueva a la más antigua, con id descendente al empatar")
    void dosPaginas() throws Exception {
        var inicio = reloj.instant().minus(Duration.ofDays(5));
        var ids = new ArrayList<Long>();
        for (var i = 0; i < 10; i++) {
            ids.add(sembrarResena(pasada(i + 1, 8), usuario, espacio, 5, "VISIBLE", inicio.plus(Duration.ofHours(i))));
        }
        // Empata en creado_en con la más nueva: el id desempata.
        ids.add(sembrarResena(pasada(1, 9), usuario, espacio, 3, "VISIBLE", inicio.plus(Duration.ofHours(9))));
        var enOrden = ids.reversed();

        var primera = lista(espacio, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.hayMas").value(true));
        var segunda = lista(espacio, "?pagina=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.hayMas").value(false));

        assertThat(idsDe(primera)).containsExactlyElementsOf(enOrden.subList(0, 10));
        assertThat(idsDe(segunda)).containsExactly(enOrden.get(10));
    }

    @Test
    @DisplayName("HU-24 R30: con exactamente 10 reseñas hayMas es false")
    void diezExactas() throws Exception {
        for (var i = 0; i < 10; i++) {
            sembrarResena(pasada(i + 1, 8), 5, "VISIBLE");
        }

        lista(espacio, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas", hasSize(10)))
                .andExpect(jsonPath("$.hayMas").value(false));
    }

    @Test
    @DisplayName("HU-24 R31: cada reseña pública trae exactamente cuatro claves y nada del autor")
    void cuatroClaves() throws Exception {
        var reserva = pasada(3, 8);
        var id = sembrarResena(reserva, 5, "VISIBLE");

        var respuesta = lista(espacio, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas[0].id").value(id))
                .andExpect(jsonPath("$.resenas[0].calificacion").value(5))
                .andExpect(jsonPath("$.resenas[0].comentario").value("Comentario 5"))
                .andExpect(jsonPath("$.resenas[0].fecha").value(hoy.toString()));

        assertThat(resenasDe(respuesta).getFirst()).containsOnlyKeys("id", "calificacion", "comentario", "fecha");
        assertThat(cuerpo(respuesta)).containsOnlyKeys("pagina", "resenas", "hayMas");
        assertThat(respuesta.andReturn().getResponse().getContentAsString())
                .doesNotContain(NOMBRE_USUARIO, CORREO_USUARIO, "usuario", "reserva", "estado", "VISIBLE");
    }

    @Test
    @DisplayName("HU-24 R32: la lista es pública, sin sesión y con un token inválido")
    void publica() throws Exception {
        sembrarResena(pasada(3, 8), 5, "VISIBLE");

        lista(espacio, "").andExpect(status().isOk()).andExpect(jsonPath("$.resenas", hasSize(1)));
        mvc.perform(con("token-inventado", get("/api/espacios/" + espacio + "/resenas")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resenas", hasSize(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAUSADO", "BAJA", "OCULTO", "BORRADOR"})
    @DisplayName("HU-24 R33: un espacio no visible responde 404 aunque tenga reseñas y la página sea mala")
    void espacioInvisible(String estado) throws Exception {
        sembrarResena(pasada(3, 8), 5, "VISIBLE");
        jdbc.update("UPDATE espacio SET estado = ? WHERE id = ?", estado, espacio);

        esNoEncontrado(lista(espacio, ""));
        esNoEncontrado(lista(espacio, "?pagina=abc"));
    }

    @Test
    @DisplayName("HU-24 R33: un arrendador suspendido oculta las reseñas de su espacio")
    void arrendadorSuspendido() throws Exception {
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendador);

        esNoEncontrado(lista(espacio, ""));
    }

    @Test
    @DisplayName("HU-24 R33: un id mal escrito o inexistente responde el mismo 404")
    void idMalo() throws Exception {
        esNoEncontrado(lista("abc", "?pagina=abc"));
        esNoEncontrado(lista("0", ""));
        esNoEncontrado(lista("99999999999999999999", ""));
        esNoEncontrado(lista(espacio + 1000, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?pagina=", "?pagina=0", "?pagina=-1", "?pagina=abc", "?pagina=1.5",
            "?pagina=2147483648"})
    @DisplayName("HU-24 R34: una página inválida responde 400 en su campo")
    void paginaInvalida(String consulta) throws Exception {
        esErrorDeCampo(lista(espacio, consulta), "pagina", M_PAGINA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?pagina=214748365", "?pagina=214748366", "?pagina=2147483647"})
    @DisplayName("HU-24 R34: una página válida enorme responde 200 con la lista vacía, no un 500")
    void paginaEnorme(String consulta) throws Exception {
        sembrarResena(pasada(3, 8), 5, "VISIBLE");

        lista(espacio, consulta).andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas", hasSize(0)))
                .andExpect(jsonPath("$.hayMas").value(false));
    }

    @Test
    @DisplayName("HU-24 R35: una reseña OCULTA no sale en la lista, el promedio ni el total")
    void ocultaFueraDeTodo() throws Exception {
        var visible = sembrarResena(pasada(1, 8), 5, "VISIBLE");
        sembrarResena(pasada(2, 8), 1, "OCULTA");

        assertThat(idsDe(lista(espacio, ""))).containsExactly(visible);
        ficha(espacio).andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").value(5.0))
                .andExpect(jsonPath("$.totalResenas").value(1));
    }

    @Test
    @DisplayName("HU-24 R36: una reseña creada sale en la siguiente lectura de la lista y de la ficha")
    void apareceAlCrearla() throws Exception {
        sembrarResena(pasada(1, 8), 5, "VISIBLE");
        var reserva = pasada(2, 8);

        var id = ((Number) cuerpo(resenar(token(CORREO_USUARIO), reserva).andExpect(status().isCreated())).get("id"))
                .longValue();

        assertThat(idsDe(lista(espacio, ""))).contains(id);
        ficha(espacio).andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").value(4.5))
                .andExpect(jsonPath("$.totalResenas").value(2));
    }
}
