package pe.edu.ulima.espacioya.reservas.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
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
 * Base de las IT de reservas: un solo contexto con el puerto real, PostgreSQL real y el reloj controlable, que empieza
 * el miércoles 2026-10-28 a las 10:00 de Lima. Las fechas se calculan desde el reloj, porque los tests lo avanzan.
 * Cada test usa su propia IP (10.14.x.x).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ReservasControllerSoporte.Dobles.class})
abstract class ReservasControllerSoporte {

    static final String RESERVAS = "/api/reservas";
    static final String CONTRASENA = "Segura123!";
    static final String CORREO_ARRENDADOR = "arrendador.secreto@correo.pe";
    static final String CORREO_USUARIO = "carla@correo.pe";
    static final String OTRO_CORREO = "beto@correo.pe";
    static final String IP_ADMIN = "127.0.0.1";
    static final String DOCUMENTO_ARRENDADOR = "20100070970";
    static final String HORARIO_NO_DISPONIBLE = "Horario no disponible. Elige otro bloque.";
    static final String CUPO =
            "Ya tienes 3 reservas pendientes de pago. Paga una o espera a que expire para reservar otra.";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_HUELLA = new AtomicInteger(1);
    private static String hashContrasena;

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RelojControlable reloj;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    PerfilArrendadorRepository perfiles;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JsonMapper json;
    @Autowired
    CifradorCampos cifrador;

    String ip;
    long arrendadorId;
    long usuarioId;
    long espacioId;
    LocalDate fecha;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @BeforeEach
    void prepararBase() {
        limpiar();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
        ip = siguienteIp();
        arrendadorId = crearArrendador();
        usuarioId = crearUsuario(CORREO_USUARIO);
        fecha = ZonaHoraria.hoy(reloj).plusDays(7);
        espacioId = publicado("80.50");
        franja(espacioId, fecha, 8, 22);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    // HU-01…HU-11 usan 10.1.x.x a 10.11.x.x; FichaControllerIT, 10.12; LimiteFichaIT, 10.13.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.14." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    long crearArrendador() {
        var usuario = usuarios.save(Usuario.registrarArrendador(CORREO_ARRENDADOR, hashContrasena, "Ana Reservada",
                "999111222", "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, DOCUMENTO_ARRENDADOR,
                String.format("%064d", SIGUIENTE_HUELLA.getAndIncrement()), "Inversiones Ocultas SAC",
                "Jr. Fiscal Escondido 777"));
        return usuario.getId();
    }

    long crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant())).getId();
    }

    // Con MFA activo (HU-17): sin él, el admin no tiene sesión.
    long crearAdmin(String correo) {
        var id = usuarios.save(Usuario.registrarAdmin(correo, hashContrasena, "2026-10", reloj.instant())).getId();
        MfaDePrueba.activar(jdbc, cifrador, id, reloj.instant());
        return id;
    }

    long publicado(String precio) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal(precio),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    void franja(long espacio, LocalDate dia, int inicio, int fin) {
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                espacio, dia.getDayOfWeek().getValue(), inicio, fin);
    }

    /** Una reserva creada directamente en la base, con un bloque por hora en {@link #fecha}. */
    long reservaEnBd(long usuario, long espacio, String estado, Instant expiraEn, boolean activo, int... horas) {
        var reserva = filaReserva(usuario, espacio, estado, expiraEn);
        for (var hora : horas) {
            filaBloque(reserva, espacio, fecha, hora, activo);
        }
        return reserva;
    }

    long filaReserva(long usuario, long espacio, String estado, Instant expiraEn) {
        return jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, 80.50, ?, ?) RETURNING id",
                Long.class, usuario, espacio, estado, Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))),
                Timestamp.from(expiraEn));
    }

    void filaBloque(long reserva, long espacio, LocalDate dia, int hora, boolean activo) {
        jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                + "VALUES (?, ?, ?, ?, ?)", reserva, espacio, Date.valueOf(dia), hora, activo);
    }

    Instant dentroDe(int minutos) {
        return reloj.instant().plus(Duration.ofMinutes(minutos));
    }

    Instant haceMinutos(int minutos) {
        return reloj.instant().minus(Duration.ofMinutes(minutos));
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud, String origen) {
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
        return solicitud;
    }

    <T extends AbstractMockHttpServletRequestBuilder<T>> T con(String token, T solicitud) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return desde(solicitud, ip);
    }

    String token(String correo) throws Exception {
        return token(correo, ip);
    }

    String token(String correo, String origen) throws Exception {
        if (usuarios.findByCorreo(correo).filter(Usuario::esAdmin).isPresent()) {
            return MfaDePrueba.iniciarSesion(mvc, jdbc, correo, CONTRASENA, origen, reloj.instant());
        }
        var cuerpo = mvc.perform(desde(post("/api/sesiones"), origen).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    ResultActions reservar(String token, long espacio, LocalDate dia, Object horas) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("espacioId", espacio);
        cuerpo.put("fecha", dia.toString());
        cuerpo.put("horas", horas);
        return enviar(token, json.writeValueAsString(cuerpo));
    }

    ResultActions enviar(String token, String cuerpo) throws Exception {
        return mvc.perform(con(token, post(RESERVAS)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    ResultActions consultar(String token, Object id) throws Exception {
        return mvc.perform(con(token, get(RESERVAS + "/" + id)));
    }

    List<Integer> bloquesLibres(long espacio, LocalDate dia) throws Exception {
        var cuerpo = mvc.perform(con(null, get("/api/espacios/" + espacio + "/disponibilidad?fecha=" + dia)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        var bloques = (List<Map<String, Object>>) mapa(cuerpo).get("bloquesLibres");
        return bloques.stream().map(bloque -> (Integer) bloque.get("horaInicio")).toList();
    }

    Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    long idDe(ResultActions respuesta) throws Exception {
        return ((Number) mapa(texto(respuesta)).get("id")).longValue();
    }

    String texto(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    int filas(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    Map<String, Object> reservaGuardada(long id) {
        return jdbc.queryForMap("SELECT * FROM reserva WHERE id = ?", id);
    }

    String estadoGuardado(long id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    List<Map<String, Object>> bloquesGuardados(long reserva) {
        return jdbc.queryForList("SELECT * FROM reserva_bloque WHERE reserva_id = ? ORDER BY hora_inicio", reserva);
    }

    int bloquesActivos(long espacio) {
        return jdbc.queryForObject("SELECT count(*) FROM reserva_bloque WHERE espacio_id = ? AND activo", Integer.class,
                espacio);
    }

    List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    static Instant instante(Object valor) {
        return valor instanceof Timestamp marca ? marca.toInstant() : ((OffsetDateTime) valor).toInstant();
    }
}
