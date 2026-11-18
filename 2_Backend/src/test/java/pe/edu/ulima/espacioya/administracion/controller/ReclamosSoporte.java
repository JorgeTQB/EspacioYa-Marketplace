package pe.edu.ulima.espacioya.administracion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las IT de HU-26: PostgreSQL real, reloj fijo en el miércoles 2026-11-04 a las 10:00 en punto de Lima (ningún
 * test lo mueve) y un doble de {@link EnvioCorreo} que guarda los correos. Siembra por JDBC lo que el test no crea por
 * la API y limpia {@code mensaje_reclamo} y {@code reclamo} antes que {@code reserva} y {@code usuario}: no tienen
 * cascada (R66). Cada test usa su propia IP (10.26.x.x); el admin entra desde 127.0.0.1.
 */
public abstract class ReclamosSoporte {

    protected static final String RECLAMOS = "/api/reclamos";
    protected static final String RECLAMABLES = "/api/reclamos/reclamables";
    protected static final String ADMIN_RECLAMOS = "/api/admin/reclamos";
    protected static final String CONTRASENA = "Segura123!";
    protected static final String CORREO_USUARIO = "carla.reclamo@correo.pe";
    protected static final String CORREO_OTRO = "beto.reclamo@correo.pe";
    protected static final String CORREO_ARRENDADOR = "ana.reclamo@correo.pe";
    protected static final String CORREO_OTRO_ARRENDADOR = "dora.reclamo@correo.pe";
    protected static final String CORREO_ADMIN = "admin.reclamo@espacioya.pe";
    protected static final String IP_ADMIN = "127.0.0.1";
    protected static final String NOMBRE_USUARIO = "Carla Reclamante";
    protected static final String TELEFONO_USUARIO = "912345678";
    protected static final String NOMBRE_ARRENDADOR = "Ana Propietaria";
    protected static final String NOMBRE_ESPACIO = "Cancha Olivos";
    protected static final String MONTO = "241.50";

    protected static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    protected static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    protected static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    protected static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";

    protected static final int SEGUNDOS_MAXIMOS = 30;
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_HUELLA = new AtomicInteger(1);
    private static String hash;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RelojControlable reloj;
    @Autowired
    protected UsuarioRepository usuarios;
    @Autowired
    protected PerfilArrendadorRepository perfiles;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected CifradorCampos cifrador;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected CorreoFalso correos;

    protected String ip;
    protected long arrendador;
    protected long usuario;
    protected long espacio;
    protected LocalDate hoy;

    @TestConfiguration(proxyBeanMethods = false)
    public static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-11-04T15:00:00Z"));
        }

        @Bean
        @Primary
        CorreoFalso correoFalso() {
            return new CorreoFalso();
        }
    }

    /** Guarda cada correo; puede fallar para un destinatario. */
    public static class CorreoFalso implements EnvioCorreo {

        private final List<MensajeCorreo> enviados = new CopyOnWriteArrayList<>();
        private volatile String fallaPara;

        @Override
        public void enviar(MensajeCorreo mensaje) {
            if (mensaje.para().equals(fallaPara)) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            enviados.add(mensaje);
        }

        public List<MensajeCorreo> enviados() {
            return List.copyOf(enviados);
        }

        public List<MensajeCorreo> para(String correo) {
            return enviados.stream().filter(mensaje -> mensaje.para().equals(correo)).toList();
        }

        public void fallarPara(String correo) {
            fallaPara = correo;
        }

        public void reiniciar() {
            enviados.clear();
            fallaPara = null;
        }
    }

    @BeforeEach
    void prepararBase() {
        limpiar();
        if (hash == null) {
            hash = encoder.encode(CONTRASENA);
        }
        correos.reiniciar();
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.26." + (n / 250) + "." + (n % 250 + 1);
        hoy = ZonaHoraria.hoy(reloj);
        arrendador = crearArrendador(CORREO_ARRENDADOR);
        jdbc.update("UPDATE usuario SET nombre = ? WHERE id = ?", NOMBRE_ARRENDADOR, arrendador);
        usuario = crearUsuario(CORREO_USUARIO);
        jdbc.update("UPDATE usuario SET nombre = ?, telefono = ? WHERE id = ?", NOMBRE_USUARIO, TELEFONO_USUARIO,
                usuario);
        espacio = espacioDe(arrendador);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reporte");
        jdbc.update("DELETE FROM mensaje_reclamo");
        jdbc.update("DELETE FROM reclamo");
        jdbc.update("DELETE FROM liquidacion_detalle");
        jdbc.update("DELETE FROM liquidacion");
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM bloqueo_fecha");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM token");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM desafio_mfa");
        jdbc.update("DELETE FROM codigo_respaldo");
        jdbc.update("DELETE FROM mfa");
        jdbc.update("DELETE FROM cuenta_bancaria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    // ── Datos ────────────────────────────────────────────────────────────

    protected long crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hash, "2026-10", reloj.instant())).getId();
    }

    protected long crearArrendador(String correo) {
        var cuenta = usuarios.save(Usuario.registrarArrendador(correo, hash, "Ana Reservada", "999111222",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(cuenta.getId(), TipoDocumento.RUC, "20100070970",
                String.format("%064d", SIGUIENTE_HUELLA.getAndIncrement()), "Inversiones Ocultas SAC",
                "Jr. Fiscal Escondido 777"));
        return cuenta.getId();
    }

    protected long crearAdmin() {
        var id = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hash, "2026-10", reloj.instant())).getId();
        MfaDePrueba.activar(jdbc, cifrador, id, reloj.instant());
        return id;
    }

    protected long espacioDe(long dueno) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, 'CANCHA', "
                        + "'Av. Universitaria 1234', 'LOS_OLIVOS', 22, 80.50, 'Cancha de grass sintético.', "
                        + "'PUBLICADO', now(), now()) RETURNING id", Long.class, dueno, NOMBRE_ESPACIO);
    }

    /** Una reserva con un bloque activo por cada hora de {@code dia}. */
    protected long reserva(long dueno, long enEspacio, String estado, LocalDate dia, int... horas) {
        var expiraEn = reloj.instant().minus(Duration.ofDays(30));
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, 241.50, ?, ?) RETURNING id",
                Long.class, dueno, enEspacio, estado, Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))),
                Timestamp.from(expiraEn));
        for (var hora : horas) {
            bloque(id, enEspacio, dia, hora, true);
        }
        return id;
    }

    protected void bloque(long reserva, long enEspacio, LocalDate dia, int hora, boolean activo) {
        jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                + "VALUES (?, ?, ?, ?, ?)", reserva, enEspacio, Date.valueOf(dia), hora, activo);
    }

    protected long confirmada(LocalDate dia, int... horas) {
        return reserva(usuario, espacio, "CONFIRMADA", dia, horas);
    }

    /** Confirmada de ayer de 8 a 9 con su pago simulado de S/ 241.50: dentro de la ventana de reclamo. */
    protected long reclamableConPago() {
        var reserva = confirmada(hoy.minusDays(1), 8);
        pago(reserva, "PAGADO");
        return reserva;
    }

    protected long pago(long reserva, String estado) {
        return jdbc.queryForObject("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, creado_en) "
                        + "VALUES (?, ?, 241.50, ?, ?) RETURNING id", Long.class, reserva,
                "sim-" + reserva + "-APROBADO-1", estado, Timestamp.from(reloj.instant().minus(Duration.ofDays(3))));
    }

    protected long sembrarReclamo(long reserva, long deUsuario, long deArrendador, Instant abiertoEn) {
        return jdbc.queryForObject("INSERT INTO reclamo (reserva_id, usuario_id, arrendador_id, motivo, descripcion, "
                        + "estado, abierto_en) VALUES (?, ?, ?, 'ESPACIO_CERRADO', 'Estaba cerrado', 'ABIERTO', ?) "
                        + "RETURNING id", Long.class, reserva, deUsuario, deArrendador, Timestamp.from(abiertoEn));
    }

    protected long sembrarReclamo(long reserva) {
        return sembrarReclamo(reserva, usuario, arrendador, reloj.instant());
    }

    protected void resolverPorJdbc(long reclamo, String estado, String monto, long admin, Instant resueltoEn) {
        jdbc.update("UPDATE reclamo SET estado = ?, resolucion = 'Resuelto', resuelto_por = ?, resuelto_en = ?, "
                        + "monto_reembolso = ?::numeric WHERE id = ?", estado, admin, Timestamp.from(resueltoEn),
                monto, reclamo);
    }

    protected long sembrarMensaje(long reclamo, long autor, String papel, String texto, Instant fecha) {
        return jdbc.queryForObject("INSERT INTO mensaje_reclamo (reclamo_id, autor_id, papel, texto, fecha) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, reclamo, autor, papel, texto,
                Timestamp.from(fecha));
    }

    protected long liquidar(long reserva, long pago, long admin) {
        var liquidacion = jdbc.queryForObject("INSERT INTO liquidacion (arrendador_id, periodo, monto_bruto, comision, "
                        + "monto_neto, estado, simulada, generada_en, generada_por) VALUES (?, ?, 241.50, 24.15, "
                        + "217.35, 'GENERADA', true, ?, ?) RETURNING id", Long.class, arrendador, Date.valueOf(hoy),
                Timestamp.from(reloj.instant()), admin);
        jdbc.update("INSERT INTO liquidacion_detalle (liquidacion_id, reserva_id, pago_id, monto, simulado) "
                + "VALUES (?, ?, ?, 241.50, true)", liquidacion, reserva, pago);
        return liquidacion;
    }

    protected int filas(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    protected Map<String, Object> reclamoDe(long reserva) {
        return jdbc.queryForMap("SELECT * FROM reclamo WHERE reserva_id = ?", reserva);
    }

    protected List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    protected static long numero(Object valor) {
        return ((Number) valor).longValue();
    }

    protected static BigDecimal decimal(Object valor) {
        return new BigDecimal(valor.toString());
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    protected <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud, String origen) {
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
        return solicitud;
    }

    protected <T extends AbstractMockHttpServletRequestBuilder<T>> T con(String token, T solicitud) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return desde(solicitud, ip);
    }

    protected <T extends AbstractMockHttpServletRequestBuilder<T>> T comoAdmin(String token, T solicitud) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return desde(solicitud, IP_ADMIN);
    }

    protected String token(String correo) throws Exception {
        if (CORREO_ADMIN.equals(correo)) {
            return MfaDePrueba.iniciarSesion(mvc, jdbc, correo, CONTRASENA, IP_ADMIN, reloj.instant());
        }
        var cuerpo = mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    protected ResultActions abrir(String token, String cuerpo) throws Exception {
        return mvc.perform(con(token, post(RECLAMOS)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    protected ResultActions abrir(String token, long reserva) throws Exception {
        return abrir(token, "{\"reservaId\":" + reserva + ",\"motivo\":\"ESPACIO_CERRADO\","
                + "\"descripcion\":\"Llegamos y estaba cerrado\"}");
    }

    protected long abrirPorLaApi(String token, long reserva) throws Exception {
        return numero(cuerpo(abrir(token, reserva).andExpect(status().isCreated())).get("id"));
    }

    protected Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    protected Map<String, Object> cuerpo(ResultActions respuesta) throws Exception {
        return mapa(respuesta.andReturn().getResponse().getContentAsString());
    }

    protected List<MvcResult> aLaVez(List<Callable<MvcResult>> solicitudes) throws Exception {
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
}
