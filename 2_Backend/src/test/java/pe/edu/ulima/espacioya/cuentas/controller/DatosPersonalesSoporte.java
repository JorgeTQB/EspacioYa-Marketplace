package pe.edu.ulima.espacioya.cuentas.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las IT de HU-22: PostgreSQL real, reloj controlable, correo sin efecto y un participante de prueba que solo
 * falla cuando el test lo activa (R35). Siembra con {@link JdbcTemplate}; cada test usa su propia IP (10.22.x.x) y
 * adelanta el reloj dos horas para que los contadores en memoria no se arrastren.
 */
abstract class DatosPersonalesSoporte {

    static final String EXPORTACIONES = "/api/usuarios/actual/exportaciones";
    static final String ELIMINACION = "/api/usuarios/actual/eliminacion";
    static final String PERFIL = "/api/usuarios/actual/perfil";
    static final String CONTRASENA = "Segura123!";
    static final String CORREO_USUARIO = "carla.datos@correo.pe";
    static final String CORREO_OTRO_USUARIO = "dani.datos@correo.pe";
    static final String CORREO_ARRENDADOR = "ana.datos@correo.pe";
    static final String CORREO_OTRO_ARRENDADOR = "beto.datos@correo.pe";
    static final String CORREO_ADMIN = "admin.datos@correo.pe";
    static final String NOMBRE_USUARIO = "Carla Reservante";
    static final String TELEFONO_USUARIO = "912345678";
    static final String NOMBRE_ARRENDADOR = "Ana Propietaria";
    static final String TELEFONO_ARRENDADOR = "999111222";
    static final String RUC = "20100070970";
    static final String RUC_OTRO = "20131312955";
    static final String RAZON = "Inversiones Privadas SAC";
    static final String DIRECCION = "Jr. Fiscal Reservado 777";
    static final String NUMERO_CUENTA = "19387654329876";
    static final String ULTIMOS4 = "7319";
    static final String IP_ADMIN = "127.0.0.1";
    static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE = new AtomicInteger(1);
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
    @Autowired
    HuellaCampos huella;
    @Autowired
    ArchivosPrivados archivos;
    @Autowired
    ParticipanteQueFalla participanteQueFalla;

    String ip;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-11-04T15:00:00Z"));
        }

        @Bean
        @Primary
        NotificadorCorreo notificadorSinEfecto() {
            return new NotificadorSinEfecto();
        }

        // Solo existe en este contexto: los demás no lo ven. Después del último bean real (HU-24 DT-14).
        @Bean
        @Order(4)
        ParticipanteQueFalla participanteQueFalla() {
            return new ParticipanteQueFalla();
        }
    }

    /** Participante extra (R45) que no exporta nada y que, activado, falla al anonimizar (R35). */
    static class ParticipanteQueFalla implements ParticipanteDatosPersonales {

        private volatile boolean activo;

        @Override
        public List<SeccionDatos> exportar(TitularDatos titular) {
            return List.of();
        }

        @Override
        public List<Impedimento> impedimentos(TitularDatos titular, Instant ahora) {
            return List.of();
        }

        @Override
        public void anonimizar(TitularDatos titular) {
            if (activo) {
                throw new IllegalStateException("el participante de prueba falló");
            }
        }

        void activar() {
            activo = true;
        }

        void reiniciar() {
            activo = false;
        }
    }

    static class NotificadorSinEfecto implements NotificadorCorreo {

        @Override
        public void notificarVerificacion(String correoNormalizado) {
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
        }

        @Override
        public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
        }

        @Override
        public void notificarCuentaReactivada(String correoNormalizado) {
        }

        @Override
        public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
        }

        @Override
        public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
        }

        @Override
        public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
        }

        @Override
        public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
        }

        @Override
        public void notificarCambioCuentaBancaria(String correoArrendador, AvisoCambioCuentaBancaria aviso) {
        }

        @Override
        public void notificarCambioCorreo(String correoNuevo, String enlace) {
        }

        @Override
        public void notificarAvisoCambioCorreo(String correoActual, String correoNuevoEnmascarado) {
        }
    }

    @BeforeEach
    void prepararBase() {
        limpiar();
        participanteQueFalla.reiniciar();
        reloj.avanzar(Duration.ofHours(2));
        ip = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
    }

    // Orden de las llaves foráneas (como PagosSoporte.limpiar), más las tablas de cuentas que toca HU-22.
    void limpiar() {
        jdbc.update("DELETE FROM liquidacion_detalle");
        jdbc.update("DELETE FROM liquidacion");
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM checkout_pago");
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM bloqueo_fecha");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM token");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM desafio_mfa");
        jdbc.update("DELETE FROM codigo_respaldo");
        jdbc.update("DELETE FROM mfa");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM revision_identidad");
        jdbc.update("DELETE FROM suspension_cuenta");
        jdbc.update("DELETE FROM cuenta_bancaria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.22." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Cuentas ──────────────────────────────────────────────────────────

    long crearUsuario(String correo) {
        var id = usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant())).getId();
        jdbc.update("UPDATE usuario SET nombre = ?, telefono = ? WHERE id = ?", NOMBRE_USUARIO, TELEFONO_USUARIO, id);
        return id;
    }

    long crearArrendador(String correo, String ruc) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, NOMBRE_ARRENDADOR,
                TELEFONO_ARRENDADOR, "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, ruc, huella.calcular(ruc), RAZON,
                DIRECCION));
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = 'VERIFICADA' WHERE usuario_id = ?",
                usuario.getId());
        return usuario.getId();
    }

    long crearAdmin() {
        var id = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hashContrasena, "2026-10", reloj.instant()))
                .getId();
        MfaDePrueba.activar(jdbc, cifrador, id, reloj.instant());
        return id;
    }

    /** Foto de perfil guardada de verdad en {@link ArchivosPrivados}; devuelve su clave. */
    String fotoDe(long usuarioId) {
        var clave = archivos.guardar("perfiles", new byte[] {1, 2, 3});
        jdbc.update("UPDATE usuario SET foto_ruta = ?, foto_tipo_mime = 'image/png' WHERE id = ?", clave, usuarioId);
        return clave;
    }

    /** Documento de identidad guardado de verdad en {@link ArchivosPrivados}; devuelve su clave. */
    String documentoDe(long arrendadorId, String tipo) {
        var clave = archivos.guardar("identidad", new byte[] {4, 5, 6});
        jdbc.update("INSERT INTO documento_identidad (arrendador_id, tipo, ruta_archivo, tipo_mime, tamano, subido_en) "
                + "VALUES (?, ?, ?, 'image/png', 3, ?)", arrendadorId, tipo, clave, Timestamp.from(reloj.instant()));
        return clave;
    }

    void correoPendiente(long usuarioId, String correo) {
        jdbc.update("UPDATE usuario SET correo_pendiente = ? WHERE id = ?", correo, usuarioId);
    }

    void tokenPendiente(long usuarioId, String tipo) {
        jdbc.update("INSERT INTO token (usuario_id, tipo, hash_token, creado_en, expira_en) VALUES (?, ?, ?, ?, ?)",
                usuarioId, tipo, TokenAleatorio.hash(TokenAleatorio.generar()), Timestamp.from(reloj.instant()),
                Timestamp.from(reloj.instant().plus(Duration.ofMinutes(30))));
    }

    void mfaCompleto(long usuarioId) {
        MfaDePrueba.activar(jdbc, cifrador, usuarioId, reloj.instant());
        jdbc.update("INSERT INTO codigo_respaldo (usuario_id, hash_codigo, creado_en) VALUES (?, ?, ?)", usuarioId,
                TokenAleatorio.hash("codigo-" + SIGUIENTE.getAndIncrement()), Timestamp.from(reloj.instant()));
        jdbc.update("INSERT INTO desafio_mfa (usuario_id, hash_token, paso, creado_en, expira_en) "
                        + "VALUES (?, ?, 'CODIGO', ?, ?)", usuarioId, TokenAleatorio.hash(TokenAleatorio.generar()),
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant().plus(Duration.ofMinutes(5))));
    }

    void cuentaBancaria(long arrendadorId) {
        jdbc.update("INSERT INTO cuenta_bancaria (arrendador_id, banco, numero, titular, creada_en, actualizada_en) "
                        + "VALUES (?, 'BCP', ?, ?, ?, ?)", arrendadorId, cifrador.cifrar(NUMERO_CUENTA), RAZON,
                Timestamp.from(reloj.instant().minus(Duration.ofDays(30))), Timestamp.from(reloj.instant()));
    }

    // ── Espacios, reservas y pagos ───────────────────────────────────────

    long espacioDe(long arrendadorId, String nombre) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, 'CANCHA', "
                + "'Av. Universitaria 1234', 'LOS_OLIVOS', 22, 50.00, 'Cancha techada.', 'PUBLICADO', now(), now()) "
                + "RETURNING id", Long.class, arrendadorId, nombre);
    }

    LocalDate hoy() {
        return ZonaHoraria.hoy(reloj);
    }

    /** Reserva de 2 bloques (S/ 100.00) desde {@code hora} en {@code dia}; una pendiente sigue vigente 10 min. */
    long reserva(long usuarioId, long espacioId, String estado, LocalDate dia, int hora) {
        var creada = reloj.instant().minus(Duration.ofDays(10));
        var expira = "PENDIENTE_PAGO".equals(estado) ? reloj.instant().plus(Duration.ofMinutes(10))
                : creada.plus(Duration.ofMinutes(15));
        if ("PENDIENTE_PAGO".equals(estado)) {
            creada = reloj.instant().minus(Duration.ofMinutes(5));
        }
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 50.00, 100.00, ?, ?) RETURNING id",
                Long.class, usuarioId, espacioId, estado, Timestamp.from(creada), Timestamp.from(expira));
        var activo = "PENDIENTE_PAGO".equals(estado) || "CONFIRMADA".equals(estado);
        for (var h = hora; h < hora + 2; h++) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, ?)", id, espacioId, Date.valueOf(dia), h, activo);
        }
        if ("CANCELADA".equals(estado)) {
            jdbc.update("UPDATE reserva SET cancelada_por = 'USUARIO', cancelada_en = ?, monto_reembolso = 100.00 "
                    + "WHERE id = ?", Timestamp.from(creada.plus(Duration.ofDays(1))), id);
        }
        return id;
    }

    /** Reseña de HU-24 sembrada con {@code creado_en} = ahora; el comentario se recibe tal cual. */
    long resena(long reservaId, long usuarioId, long espacioId, int calificacion, String comentario, String estado) {
        return jdbc.queryForObject("INSERT INTO resena (reserva_id, usuario_id, espacio_id, calificacion, comentario, "
                        + "estado, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, reservaId,
                usuarioId, espacioId, calificacion, comentario, estado, Timestamp.from(reloj.instant()));
    }

    long pago(long reservaId, String estado) {
        return jdbc.queryForObject("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, "
                        + "creado_en) VALUES (?, ?, 100.00, ?, ?, ?) RETURNING id", Long.class, reservaId,
                idTransaccion(), estado, ULTIMOS4, Timestamp.from(reloj.instant().minus(Duration.ofDays(9))));
    }

    static String idTransaccion() {
        return "MP-HU22-" + SIGUIENTE.getAndIncrement();
    }

    void reembolsoPendiente(long pagoId) {
        jdbc.update("INSERT INTO reembolso (pago_id, monto, origen, estado, creado_en) "
                + "VALUES (?, 100.00, 'CANCELACION', 'PENDIENTE', ?)", pagoId, Timestamp.from(reloj.instant()));
    }

    long liquidacion(long arrendadorId, String estado, long adminId) {
        var bruto = new BigDecimal("100.00");
        var comision = bruto.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        var generada = reloj.instant().minus(Duration.ofDays(2));
        var id = jdbc.queryForObject("INSERT INTO liquidacion (arrendador_id, periodo, monto_bruto, comision, "
                        + "monto_neto, estado, simulada, generada_en, generada_por) "
                        + "VALUES (?, ?, ?, ?, ?, ?, false, ?, ?) RETURNING id", Long.class, arrendadorId,
                Date.valueOf(LocalDate.ofInstant(generada, ZonaHoraria.LIMA)), bruto, comision,
                bruto.subtract(comision), "PAGADA".equals(estado) ? "GENERADA" : estado, Timestamp.from(generada),
                adminId);
        if ("PAGADA".equals(estado)) {
            jdbc.update("UPDATE liquidacion SET estado = 'PAGADA', numero_operacion = 'OP-2201', pagada_en = ?, "
                    + "pagada_por = ?, banco_destino = 'BCP', cuenta_destino = '****9876' WHERE id = ?",
                    Timestamp.from(reloj.instant().minus(Duration.ofDays(1))), adminId, id);
        }
        return id;
    }

    void lineaDe(long liquidacionId, long reservaId, long pagoId) {
        jdbc.update("INSERT INTO liquidacion_detalle (liquidacion_id, reserva_id, pago_id, monto, simulado) "
                + "VALUES (?, ?, ?, 100.00, false)", liquidacionId, reservaId, pagoId);
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
        if (CORREO_ADMIN.equals(correo)) {
            return MfaDePrueba.iniciarSesion(mvc, jdbc, correo, CONTRASENA, IP_ADMIN, reloj.instant());
        }
        var cuerpo = iniciarSesion(correo, CONTRASENA).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"));
    }

    ResultActions exportar(String token, String contrasena) throws Exception {
        return mvc.perform(con(token, post(EXPORTACIONES)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(contrasena)));
    }

    ResultActions eliminar(String token, String contrasena) throws Exception {
        return mvc.perform(con(token, post(ELIMINACION)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(contrasena)));
    }

    private static String cuerpo(String contrasena) {
        return contrasena == null ? "{}" : "{\"contrasena\":\"" + contrasena + "\"}";
    }

    Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    String texto(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    Map<String, Object> filaUsuario(long id) {
        return jdbc.queryForMap("SELECT * FROM usuario WHERE id = ?", id);
    }

    Map<String, Object> filaPerfil(long id) {
        return jdbc.queryForMap("SELECT * FROM perfil_arrendador WHERE usuario_id = ?", id);
    }

    int contar(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Integer.class, argumentos);
    }
}
