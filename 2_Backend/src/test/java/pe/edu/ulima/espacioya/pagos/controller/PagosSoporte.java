package pe.edu.ulima.espacioya.pagos.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las IT de pagos: PostgreSQL real, reloj controlable que empieza el miércoles 2026-10-28 a las 10:00 de Lima y
 * un doble de {@link NotificadorCorreo} que guarda los correos. Limpia {@code pago} antes que {@code reserva}: no tiene
 * cascada. Cada test usa su propia IP (10.15.x.x).
 */
abstract class PagosSoporte {

    static final String CONTRASENA = "Segura123!";
    static final String CORREO_ARRENDADOR = "arrendador.pagos@correo.pe";
    static final String CORREO_USUARIO = "carla.pagos@correo.pe";
    static final String OTRO_CORREO = "beto.pagos@correo.pe";
    static final String IP_ADMIN = "127.0.0.1";
    static final String MONTO = "241.50";
    static final String NOMBRE_ESPACIO = "Cancha Los Olivos";
    static final String NO_DISPONIBLE = "Esta reserva ya no se puede pagar. Busca otro horario para reservar de nuevo.";
    static final String DEMASIADOS_INTENTOS = "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    static final String PASARELA_NO_DISPONIBLE =
            "No pudimos conectar con el servicio de pagos. Intenta de nuevo en unos minutos.";

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
    NotificadorFalso notificador;

    String ip;
    long arrendadorId;
    long usuarioId;
    long espacioId;
    LocalDate fecha;

    record Comprobante(String correo, ComprobantePago datos) {
    }

    record AvisoArrendador(String correo, AvisoReservaPagada datos) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }

        @Bean
        @Primary
        NotificadorFalso notificadorFalso() {
            return new NotificadorFalso();
        }
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<Comprobante> comprobantes = new CopyOnWriteArrayList<>();
        private final List<AvisoArrendador> avisos = new CopyOnWriteArrayList<>();
        private volatile boolean falla;

        @Override
        public void notificarVerificacion(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-13 no registra cuentas por la API");
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
            throw new UnsupportedOperationException("HU-13 no recupera contraseñas");
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-13 no revisa identidades");
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
            throw new UnsupportedOperationException("HU-13 no revisa identidades");
        }

        @Override
        public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
            throw new UnsupportedOperationException("HU-13 no suspende cuentas");
        }

        @Override
        public void notificarCuentaReactivada(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-13 no reactiva cuentas");
        }

        @Override
        public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
            if (falla) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            comprobantes.add(new Comprobante(correoNormalizado, comprobante));
        }

        @Override
        public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
            if (falla) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            avisos.add(new AvisoArrendador(correoArrendador, aviso));
        }

        List<Comprobante> comprobantes() {
            return comprobantes;
        }

        List<AvisoArrendador> avisos() {
            return avisos;
        }

        void fallar() {
            falla = true;
        }

        void reiniciar() {
            comprobantes.clear();
            avisos.clear();
            falla = false;
        }
    }

    @BeforeEach
    void prepararBase() {
        limpiar();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
        notificador.reiniciar();
        ip = siguienteIp();
        arrendadorId = crearArrendador();
        usuarioId = crearUsuario(CORREO_USUARIO);
        fecha = ZonaHoraria.hoy(reloj).plusDays(7);
        espacioId = publicado();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM bloqueo_fecha");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    // HU-12 usa 10.14.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.15." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    long crearArrendador() {
        var usuario = usuarios.save(Usuario.registrarArrendador(CORREO_ARRENDADOR, hashContrasena, "Ana Reservada",
                "999111222", "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970",
                String.format("%064d", SIGUIENTE_HUELLA.getAndIncrement()), "Inversiones Ocultas SAC",
                "Jr. Fiscal Escondido 777"));
        return usuario.getId();
    }

    long crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant())).getId();
    }

    long crearAdmin(String correo) {
        return usuarios.save(Usuario.registrarAdmin(correo, hashContrasena, "2026-10", reloj.instant())).getId();
    }

    long publicado() {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, NOMBRE_ESPACIO, "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal("80.50"),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    /** Una reserva de S/ 241.50 con 3 bloques, de 14:00 a 17:00, creada directamente en la base. */
    long reserva(long usuario, String estado, Instant expiraEn) {
        var creadaEn = expiraEn.minus(Duration.ofMinutes(15));
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, 241.50, ?, ?) RETURNING id",
                Long.class, usuario, espacioId, estado, Timestamp.from(creadaEn), Timestamp.from(expiraEn));
        for (var hora : List.of(14, 15, 16)) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, ?)", id, espacioId, Date.valueOf(fecha), hora,
                    !"EXPIRADA".equals(estado));
        }
        return id;
    }

    long vigente() {
        return reserva(usuarioId, "PENDIENTE_PAGO", dentroDe(10));
    }

    void filaPago(long reserva, String idTransaccion, String estado, Instant creadoEn) {
        jdbc.update("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, creado_en) "
                + "VALUES (?, ?, 241.50, ?, ?)", reserva, idTransaccion, estado, Timestamp.from(creadoEn));
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
        var cuerpo = mvc.perform(desde(post("/api/sesiones"), origen).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    ResultActions pagar(String token, Object reserva) throws Exception {
        return mvc.perform(con(token, post("/api/reservas/" + reserva + "/pago")));
    }

    ResultActions estadoDePago(String token, Object reserva) throws Exception {
        return mvc.perform(con(token, get("/api/reservas/" + reserva + "/pago")));
    }

    Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    String texto(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    int filas(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    String estadoDeReserva(long id) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
    }

    List<Map<String, Object>> pagosDe(long reserva) {
        return jdbc.queryForList("SELECT * FROM pago WHERE reserva_id = ? ORDER BY id", reserva);
    }

    int bloquesActivosDe(long reserva) {
        return jdbc.queryForObject("SELECT count(*) FROM reserva_bloque WHERE reserva_id = ? AND activo", Integer.class,
                reserva);
    }

    List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }
}
