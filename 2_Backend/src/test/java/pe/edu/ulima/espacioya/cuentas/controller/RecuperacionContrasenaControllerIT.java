package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.Token;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.RestablecimientoContrasenaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.cuentas.service.RecuperacionContrasenaService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecuperacionContrasenaControllerIT.Dobles.class})
class RecuperacionContrasenaControllerIT {

    private static final String SOLICITUD_RECIBIDA = "Si el correo está registrado, te enviamos un enlace para "
            + "restablecer tu contraseña. Revisa tu bandeja de entrada.";
    private static final String ENLACE_INVALIDO =
            "El enlace para restablecer tu contraseña no es válido o ya expiró. Solicita uno nuevo.";
    private static final String CONTRASENA_RESTABLECIDA =
            "Tu contraseña se cambió. Inicia sesión con tu nueva contraseña.";
    private static final String REQUISITOS = "La contraseña debe tener entre 8 y 64 caracteres, "
            + "al menos una letra y un número, y no puede ser igual a tu correo.";
    private static final String CORREO_INVALIDO = "Ingresa un correo válido.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String FRONTEND = "http://localhost:5173";
    private static final String PREFIJO_ENLACE = FRONTEND + "/restablecer-contrasena#token=";

    private static final String CORREO = "ana@correo.pe";
    private static final String OTRO_CORREO = "beto@correo.pe";
    private static final String ANTERIOR = "Segura123!";
    private static final String NUEVA = "NuevaClave456";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static String hashAnterior;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private NotificadorFalso notificador;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private TokenRepository tokens;
    @Autowired
    private RecuperacionContrasenaService servicio;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JsonMapper json;

    private String ip;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        NotificadorFalso notificadorFalso() {
            return new NotificadorFalso();
        }

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    record Envio(String correo, String enlace) {
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<Envio> envios = new CopyOnWriteArrayList<>();

        @Override
        public void notificarVerificacion(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-04 no registra cuentas por la API");
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
            envios.add(new Envio(correoNormalizado, enlace));
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-06 no se prueba en esta clase");
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
            throw new UnsupportedOperationException("HU-06 no se prueba en esta clase");
        }

        List<Envio> envios() {
            return envios;
        }

        void reiniciar() {
            envios.clear();
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM token");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        notificador.reiniciar();
        // Los contadores de solicitudes y de fallos viven en memoria y se comparten entre tests:
        // una hora después ya no cuenta nada.
        reloj.avanzar(Duration.ofHours(1));
        ip = siguienteIp();
        if (hashAnterior == null) {
            hashAnterior = encoder.encode(ANTERIOR);
        }
    }

    // HU-01 usa 10.1.x.x, HU-02 10.2.x.x y HU-03 10.3.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.4." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashAnterior, "2026-10", reloj.instant()));
    }

    private static String cuerpoCorreo(String correo) {
        return "{\"correo\":\"" + correo + "\"}";
    }

    private static String cuerpoToken(String token) {
        return token == null ? "{}" : "{\"token\":\"" + token + "\"}";
    }

    private static String cuerpoConfirmacion(String token, String contrasena) {
        return "{\"token\":\"" + token + "\",\"contrasena\":\"" + contrasena + "\"}";
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder solicitud) {
        var origen = ip;
        return solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
    }

    private ResultActions enviar(String ruta, String cuerpo) throws Exception {
        return mvc.perform(desde(post(ruta)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions solicitar(String correo) throws Exception {
        return enviar("/api/recuperaciones", cuerpoCorreo(correo));
    }

    private ResultActions validar(String token) throws Exception {
        return enviar("/api/recuperaciones/validaciones", cuerpoToken(token));
    }

    private ResultActions confirmar(String token, String contrasena) throws Exception {
        return enviar("/api/recuperaciones/confirmaciones", cuerpoConfirmacion(token, contrasena));
    }

    private String cuerpoDe(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    private String solicitarToken(String correo) throws Exception {
        var antes = notificador.envios().size();
        solicitar(correo).andExpect(status().isAccepted());
        assertThat(notificador.envios()).hasSize(antes + 1);
        return tokenDe(notificador.envios().getLast().enlace());
    }

    private static String tokenDe(String enlace) {
        assertThat(enlace).startsWith(PREFIJO_ENLACE);
        return enlace.substring(PREFIJO_ENLACE.length());
    }

    private ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return enviar("/api/sesiones", "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}");
    }

    private String tokenSesion(String correo, String contrasena) throws Exception {
        var cuerpo = cuerpoDe(iniciarSesion(correo, contrasena).andExpect(status().isCreated()));
        return (String) mapa(cuerpo).get("token");
    }

    private ResultActions sesionActual(String tokenSesion) throws Exception {
        return mvc.perform(desde(get("/api/sesiones/actual"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSesion));
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private int filas(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private Map<String, Object> tokenGuardado(String token) {
        return jdbc.queryForMap("SELECT * FROM token WHERE hash_token = ?", TokenAleatorio.hash(token));
    }

    private String hashContrasena(String correo) {
        return jdbc.queryForObject("SELECT hash_contrasena FROM usuario WHERE correo = ?", String.class, correo);
    }

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    private List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    private void esperaEnlaceInvalido(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(ENLACE_INVALIDO))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    private void esperaSolicitudRecibida(ResultActions resultado) throws Exception {
        var cuerpo = cuerpoDe(resultado.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(SOLICITUD_RECIBIDA)));
        assertThat(mapa(cuerpo).keySet()).containsExactly("mensaje");
    }

    // ── CA1: solicitud del enlace ────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R1: con un correo registrado responde 202 solo con el mensaje de solicitud recibida")
    void solicitudConCuentaResponde202() throws Exception {
        crearUsuario(CORREO);

        esperaSolicitudRecibida(solicitar(CORREO));
    }

    @Test
    @DisplayName("HU-04 R1, R7: un correo no registrado recibe la misma respuesta, sin token ni correo enviado")
    void correoDesconocidoMismaRespuesta() throws Exception {
        crearUsuario(CORREO);
        var conCuenta = cuerpoDe(solicitar(CORREO).andExpect(status().isAccepted()));
        notificador.reiniciar();
        jdbc.update("DELETE FROM token");

        var sinCuenta = cuerpoDe(solicitar("nadie@correo.pe").andExpect(status().isAccepted()));

        assertThat(sinCuenta).isEqualTo(conCuenta);
        assertThat(filas("token")).isZero();
        assertThat(notificador.envios()).isEmpty();
    }

    @Test
    @DisplayName("HU-04 R1, R8: cuentas suspendida, admin o con correo sin verificar → misma respuesta y sin token")
    void cuentasNoHabilitadasMismaRespuesta() throws Exception {
        crearUsuario("suspendida@correo.pe");
        crearUsuario("admin@correo.pe");
        crearUsuario("sinverificar@correo.pe");
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE correo = 'suspendida@correo.pe'");
        jdbc.update("UPDATE usuario SET rol = 'ADMIN' WHERE correo = 'admin@correo.pe'");
        jdbc.update("UPDATE usuario SET correo_verificado = false WHERE correo = 'sinverificar@correo.pe'");

        for (var correo : List.of("suspendida@correo.pe", "admin@correo.pe", "sinverificar@correo.pe")) {
            esperaSolicitudRecibida(solicitar(correo));
        }

        assertThat(filas("token")).isZero();
        assertThat(notificador.envios()).isEmpty();
    }

    @Test
    @DisplayName("HU-06 R6: un admin desde una IP permitida tampoco recupera la contraseña (sin token ni correo)")
    void adminDesdeIpPermitidaSinRecuperacion() throws Exception {
        var admin = crearUsuario("admin@correo.pe");
        jdbc.update("UPDATE usuario SET rol = 'ADMIN' WHERE id = ?", admin.getId());
        ip = "127.0.0.1";

        esperaSolicitudRecibida(solicitar("admin@correo.pe"));

        assertThat(filas("token")).isZero();
        assertThat(notificador.envios()).isEmpty();
        assertThat(auditorias("RECUPERACION_NO_EMITIDA")).extracting(fila -> fila.get("detalle"))
                .containsExactly("motivo=ROL_NO_PERMITIDO");
    }

    @Test
    @DisplayName("HU-04 R1, R9: la 4.ª solicitud en la hora recibe la misma respuesta y no genera token")
    void cuartaSolicitudMismaRespuestaSinToken() throws Exception {
        crearUsuario(CORREO);
        for (int i = 0; i < 3; i++) {
            solicitarToken(CORREO);
        }

        esperaSolicitudRecibida(solicitar(CORREO));

        assertThat(filas("token")).isEqualTo(3);
        assertThat(notificador.envios()).hasSize(3);
    }

    @Test
    @DisplayName("HU-04 R12: un correo vacío, sin formato o de más de 254 caracteres → 400 en correo, sin token")
    void correoInvalidoResponde400() throws Exception {
        crearUsuario(CORREO);
        var largo = "a".repeat(250) + "@c.pe";

        for (var cuerpo : List.of(cuerpoCorreo(""), cuerpoCorreo("sin-arroba"), cuerpoCorreo(largo), "{}")) {
            enviar("/api/recuperaciones", cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(CORREO_INVALIDO));
        }

        assertThat(filas("token")).isZero();
        assertThat(notificador.envios()).isEmpty();
    }

    @Test
    @DisplayName("HU-04 R2, R3: guarda el token con la cuenta, RECUPERACION, fechas del reloj (+30 min) y solo su hash")
    void guardaTokenConHashYFechas() throws Exception {
        var usuario = crearUsuario(CORREO);
        var ahora = reloj.instant();

        var token = solicitarToken(CORREO);

        var fila = jdbc.queryForMap("SELECT * FROM token");
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(fila.get("tipo")).isEqualTo("RECUPERACION");
        assertThat(instante(fila.get("creado_en"))).isEqualTo(ahora);
        assertThat(instante(fila.get("expira_en"))).isEqualTo(ahora.plus(Duration.ofMinutes(30)));
        assertThat(fila.get("usado_en")).isNull();
        assertThat(fila.get("hash_token")).isEqualTo(TokenAleatorio.hash(token));
        assertThat(fila.values()).noneMatch(valor -> String.valueOf(valor).contains(token));
    }

    @Test
    @DisplayName("HU-04 R4: dos solicitudes de la misma cuenta reciben tokens distintos")
    void dosSolicitudesTokensDistintos() throws Exception {
        crearUsuario(CORREO);

        var primero = solicitarToken(CORREO);
        var segundo = solicitarToken(CORREO);

        assertThat(primero).isNotEqualTo(segundo);
        assertThat(filas("token")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-04 R5: notifica una vez con el correo normalizado y el enlace del frontend con el token")
    void notificaConCorreoNormalizadoYEnlace() throws Exception {
        crearUsuario(CORREO);

        solicitar("ANA@Correo.PE").andExpect(status().isAccepted());

        assertThat(notificador.envios()).hasSize(1);
        var envio = notificador.envios().getFirst();
        assertThat(envio.correo()).isEqualTo(CORREO);
        assertThat(envio.enlace()).matches("^" + PREFIJO_ENLACE + "[A-Za-z0-9_-]{43}$");
    }

    @Test
    @DisplayName("HU-04 R6: pedir un enlace nuevo anula el anterior, que ya no se acepta")
    void enlaceNuevoAnulaElAnterior() throws Exception {
        crearUsuario(CORREO);
        var primero = solicitarToken(CORREO);
        reloj.avanzar(Duration.ofMinutes(1));

        var segundo = solicitarToken(CORREO);

        assertThat(instante(tokenGuardado(primero).get("usado_en"))).isEqualTo(reloj.instant());
        esperaEnlaceInvalido(validar(primero));
        validar(segundo).andExpect(status().isNoContent());
    }

    // ── Límite de solicitudes (STRIDE D) ─────────────────────────────────

    @Test
    @DisplayName("HU-04 R9, R10: tras 3 solicitudes la 4.ª no genera token; una hora después vuelve a emitir")
    void limiteSeLiberaTrasUnaHora() throws Exception {
        crearUsuario(CORREO);
        for (int i = 0; i < 3; i++) {
            solicitarToken(CORREO);
        }
        solicitar(CORREO).andExpect(status().isAccepted());
        assertThat(filas("token")).isEqualTo(3);

        reloj.avanzar(Duration.ofHours(1));
        solicitarToken(CORREO);

        assertThat(filas("token")).isEqualTo(4);
    }

    @Test
    @DisplayName("HU-04 R10: las solicitudes de un correo sin cuenta consumen el cupo de ese correo")
    void correoSinCuentaConsumeCupo() throws Exception {
        for (int i = 0; i < 3; i++) {
            solicitar(CORREO).andExpect(status().isAccepted());
        }
        crearUsuario(CORREO);

        esperaSolicitudRecibida(solicitar(CORREO));

        assertThat(filas("token")).isZero();
        assertThat(notificador.envios()).isEmpty();
    }

    @Test
    @DisplayName("HU-04 R11: ni los 400 ni las solicitudes por encima del límite consumen cupo")
    void rechazadasNoConsumenCupo() throws Exception {
        crearUsuario(CORREO);
        for (int i = 0; i < 3; i++) {
            enviar("/api/recuperaciones", "{\"correo\":\"" + CORREO + "\"").andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 3; i++) {
            solicitarToken(CORREO);
        }
        reloj.avanzar(Duration.ofMinutes(30));
        solicitar(CORREO).andExpect(status().isAccepted());
        solicitar(CORREO).andExpect(status().isAccepted());
        assertThat(filas("token")).isEqualTo(3);

        // A la hora de las 3 primeras, las rechazadas de hace 30 min no ocupan cupo: se emiten 3 más.
        reloj.avanzar(Duration.ofMinutes(30));
        for (int i = 0; i < 3; i++) {
            solicitarToken(CORREO);
        }

        assertThat(filas("token")).isEqualTo(6);
    }

    // ── CA2: apertura del enlace ─────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R15: un token vigente de una cuenta habilitada responde 204 y no cambia el token")
    void validarTokenVigente() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);
        var antes = tokenGuardado(token);

        validar(token).andExpect(status().isNoContent());

        assertThat(tokenGuardado(token)).isEqualTo(antes);
    }

    @Test
    @DisplayName("HU-04 R16, R17: token sin forma válida o desconocido → 400 con el mensaje de enlace inválido")
    void validarTokenInvalidoODesconocido() throws Exception {
        crearUsuario(CORREO);
        solicitarToken(CORREO);

        for (var token : new String[] {null, "", "corto", TokenAleatorio.generar() + "A", "+".repeat(43),
                TokenAleatorio.generar()}) {
            esperaEnlaceInvalido(validar(token));
        }
    }

    @Test
    @DisplayName("HU-04 R16: a los 30 minutos exactos el enlace expiró y se rechaza")
    void validarTokenExpirado() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);

        reloj.avanzar(Duration.ofMinutes(30).minusSeconds(1));
        validar(token).andExpect(status().isNoContent());
        reloj.avanzar(Duration.ofSeconds(1));

        esperaEnlaceInvalido(validar(token));
        esperaEnlaceInvalido(confirmar(token, NUEVA));
        assertThat(hashContrasena(CORREO)).isEqualTo(hashAnterior);
    }

    @Test
    @DisplayName("HU-04 R16: un enlace cuya cuenta se suspendió después de emitirlo se rechaza al abrir y al confirmar")
    void validarTokenDeCuentaSuspendida() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);
        var sesion = tokenSesion(CORREO, ANTERIOR);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE correo = ?", CORREO);

        esperaEnlaceInvalido(validar(token));
        esperaEnlaceInvalido(confirmar(token, NUEVA));

        assertThat(hashContrasena(CORREO)).isEqualTo(hashAnterior);
        assertThat(tokenGuardado(token).get("usado_en")).isNull();
        assertThat(jdbc.queryForObject("SELECT revocada FROM sesion WHERE hash_token = ?", Boolean.class,
                TokenAleatorio.hash(sesion))).isFalse();
    }

    // ── CA3: restablecimiento ────────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R18, R25: cambia la contraseña y responde 200 solo con el mensaje, sin crear sesión")
    void restableceYRespondeSoloMensaje() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);

        var cuerpo = cuerpoDe(confirmar(token, NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(CONTRASENA_RESTABLECIDA)));

        assertThat(mapa(cuerpo).keySet()).containsExactly("mensaje");
        assertThat(filas("sesion")).isZero();
        var hash = hashContrasena(CORREO);
        assertThat(hash).startsWith("$2").isNotEqualTo(hashAnterior);
        assertThat(encoder.matches(NUEVA, hash)).isTrue();
        iniciarSesion(CORREO, NUEVA).andExpect(status().isCreated());
        iniciarSesion(CORREO, ANTERIOR).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HU-04 R19, R16: el token queda usado y un segundo uso se rechaza sin cambiar la contraseña")
    void segundoUsoRechazado() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);
        confirmar(token, NUEVA).andExpect(status().isOk());
        var hash = hashContrasena(CORREO);

        assertThat(instante(tokenGuardado(token).get("usado_en"))).isEqualTo(reloj.instant());
        esperaEnlaceInvalido(validar(token));
        esperaEnlaceInvalido(confirmar(token, "OtraClave789"));
        assertThat(hashContrasena(CORREO)).isEqualTo(hash);
    }

    @Test
    @DisplayName("HU-04 R20: al restablecer se anulan los demás tokens vigentes de la cuenta")
    void restablecerAnulaLosDemasTokens() throws Exception {
        var usuario = crearUsuario(CORREO);
        crearUsuario(OTRO_CORREO);
        var token = solicitarToken(CORREO);
        var pendiente = TokenAleatorio.generar();
        tokens.save(Token.recuperacion(usuario.getId(), TokenAleatorio.hash(pendiente), reloj.instant()));
        var ajeno = solicitarToken(OTRO_CORREO);
        validar(pendiente).andExpect(status().isNoContent());

        confirmar(token, NUEVA).andExpect(status().isOk());

        assertThat(instante(tokenGuardado(pendiente).get("usado_en"))).isEqualTo(reloj.instant());
        esperaEnlaceInvalido(validar(pendiente));
        validar(ajeno).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("HU-04 R21: al restablecer se revocan todas las sesiones abiertas de la cuenta y solo de ella")
    void restablecerRevocaTodasLasSesiones() throws Exception {
        crearUsuario(CORREO);
        crearUsuario(OTRO_CORREO);
        var primera = tokenSesion(CORREO, ANTERIOR);
        var segunda = tokenSesion(CORREO, ANTERIOR);
        var ajena = tokenSesion(OTRO_CORREO, ANTERIOR);
        var token = solicitarToken(CORREO);

        confirmar(token, NUEVA).andExpect(status().isOk());

        sesionActual(primera).andExpect(status().isUnauthorized());
        sesionActual(segunda).andExpect(status().isUnauthorized());
        sesionActual(ajena).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion WHERE revocada", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-04 R22: dos confirmaciones simultáneas con el mismo token, solo una cambia la contraseña")
    void soloUnaConfirmacionGana() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);
        var listos = new CountDownLatch(1);
        Callable<Boolean> restablecer = () -> {
            listos.await();
            try {
                servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), ip);
                return true;
            } catch (ReglaNegocioException e) {
                return false;
            }
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(restablecer);
            var b = hilos.submit(restablecer);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }

        assertThat(auditorias(RecuperacionContrasenaService.ACCION_RESTABLECIDA)).hasSize(1);
        assertThat(auditorias(RecuperacionContrasenaService.ACCION_RECHAZADA)).singleElement()
                .satisfies(fila -> assertThat(fila.get("detalle")).isEqualTo("motivo=TOKEN_USADO"));
    }

    @Test
    @DisplayName("HU-04 R23: contraseña sin dígito, igual al correo o nula → 400 en contrasena; el token no se usa")
    void contrasenaQueNoCumpleLaPolitica() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);

        for (var cuerpo : List.of(cuerpoConfirmacion(token, "SinDigitos"), cuerpoConfirmacion(token, CORREO),
                "{\"token\":\"" + token + "\"}")) {
            enviar("/api/recuperaciones/confirmaciones", cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(REQUISITOS));
        }

        assertThat(tokenGuardado(token).get("usado_en")).isNull();
        assertThat(hashContrasena(CORREO)).isEqualTo(hashAnterior);
        confirmar(token, NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-04 R24: tras agotar los intentos de inicio de sesión, restablecer permite volver a entrar")
    void restablecerReiniciaLosFallosDeLogin() throws Exception {
        crearUsuario(CORREO);
        for (int i = 0; i < 5; i++) {
            iniciarSesion(CORREO, "Errada9999").andExpect(status().isUnauthorized());
        }
        iniciarSesion(CORREO, ANTERIOR).andExpect(status().isTooManyRequests());
        var token = solicitarToken(CORREO);

        confirmar(token, NUEVA).andExpect(status().isOk());

        iniciarSesion(CORREO, NUEVA).andExpect(status().isCreated());
    }

    // ── Auditoría (STRIDE R) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R26, R30: la emisión audita RECUPERACION_SOLICITADA con la cuenta, el token, la IP y la fecha")
    void auditaSolicitudEmitida() throws Exception {
        var usuario = crearUsuario(CORREO);
        var token = solicitarToken(CORREO);

        var fila = auditorias(RecuperacionContrasenaService.ACCION_SOLICITADA).getFirst();
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(fila.get("entidad")).isEqualTo("token");
        assertThat(((Number) fila.get("entidad_id")).longValue())
                .isEqualTo(((Number) tokenGuardado(token).get("id")).longValue());
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(fila.get("detalle")).isNull();
    }

    @Test
    @DisplayName("HU-04 R27, R30: las solicitudes sin enlace auditan RECUPERACION_NO_EMITIDA con su motivo, sin correo")
    void auditaSolicitudesNoEmitidas() throws Exception {
        var usuario = crearUsuario(CORREO);
        var suspendida = crearUsuario("suspendida@correo.pe");
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", suspendida.getId());
        solicitar("nadie@correo.pe").andExpect(status().isAccepted());
        solicitar("suspendida@correo.pe").andExpect(status().isAccepted());
        for (int i = 0; i < 4; i++) {
            solicitar(CORREO).andExpect(status().isAccepted());
        }

        var filas = auditorias(RecuperacionContrasenaService.ACCION_NO_EMITIDA);

        assertThat(filas).extracting(fila -> fila.get("detalle")).containsExactly(
                "motivo=CORREO_DESCONOCIDO", "motivo=CUENTA_NO_ACTIVA", "motivo=LIMITE_SUPERADO");
        assertThat(filas.get(0).get("usuario_id")).isNull();
        assertThat(((Number) filas.get(1).get("usuario_id")).longValue()).isEqualTo(suspendida.getId());
        assertThat(((Number) filas.get(2).get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(filas).allSatisfy(fila -> {
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
            assertThat((String) fila.get("detalle")).doesNotContain("@");
        });
    }

    @Test
    @DisplayName("HU-04 R28, R30: el restablecimiento audita CONTRASENA_RESTABLECIDA con las sesiones revocadas")
    void auditaRestablecimiento() throws Exception {
        var usuario = crearUsuario(CORREO);
        tokenSesion(CORREO, ANTERIOR);
        tokenSesion(CORREO, ANTERIOR);
        var token = solicitarToken(CORREO);

        confirmar(token, NUEVA).andExpect(status().isOk());

        var fila = auditorias(RecuperacionContrasenaService.ACCION_RESTABLECIDA).getFirst();
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(fila.get("entidad")).isEqualTo("usuario");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(fila.get("detalle")).isEqualTo("sesionesRevocadas=2");
    }

    @Test
    @DisplayName("HU-04 R29, R30: cada confirmación rechazada audita RECUPERACION_RECHAZADA con su motivo")
    void auditaConfirmacionesRechazadas() throws Exception {
        var usuario = crearUsuario(CORREO);
        var usado = solicitarToken(CORREO);
        confirmar(usado, NUEVA).andExpect(status().isOk());
        var expirado = solicitarToken(CORREO);
        reloj.avanzar(Duration.ofMinutes(30));
        confirmar(expirado, NUEVA).andExpect(status().isBadRequest());
        var deSuspendida = solicitarToken(CORREO);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", usuario.getId());
        var desconocido = TokenAleatorio.generar();

        confirmar(usado, NUEVA).andExpect(status().isBadRequest());
        confirmar(deSuspendida, NUEVA).andExpect(status().isBadRequest());
        confirmar(desconocido, NUEVA).andExpect(status().isBadRequest());
        confirmar("forma-invalida", NUEVA).andExpect(status().isBadRequest());

        var filas = auditorias(RecuperacionContrasenaService.ACCION_RECHAZADA);
        assertThat(filas).extracting(fila -> fila.get("detalle")).containsExactly("motivo=TOKEN_EXPIRADO",
                "motivo=TOKEN_USADO", "motivo=CUENTA_NO_HABILITADA", "motivo=TOKEN_DESCONOCIDO",
                "motivo=TOKEN_DESCONOCIDO");
        assertThat(filas.subList(0, 3)).allSatisfy(fila ->
                assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario.getId()));
        assertThat(filas.subList(3, 5)).allSatisfy(fila -> assertThat(fila.get("usuario_id")).isNull());
        assertThat(filas).allSatisfy(fila -> assertThat(fila.get("ip")).isEqualTo(ip));
    }

    @Test
    @DisplayName("HU-04 R30: ninguna auditoría de la recuperación guarda el token, su hash, el correo ni la contraseña")
    void auditoriasSinDatosSensibles() throws Exception {
        crearUsuario(CORREO);
        var token = solicitarToken(CORREO);
        confirmar(token, "SinDigitos").andExpect(status().isBadRequest());
        confirmar(token, NUEVA).andExpect(status().isOk());
        confirmar(token, NUEVA).andExpect(status().isBadRequest());
        solicitar("nadie@correo.pe").andExpect(status().isAccepted());

        var filas = jdbc.queryForList("SELECT * FROM auditoria WHERE accion LIKE 'RECUPERACION_%' "
                + "OR accion = 'CONTRASENA_RESTABLECIDA'");

        assertThat(filas).hasSize(4);
        assertThat(filas).allSatisfy(fila -> assertThat(fila.values()).noneMatch(valor -> {
            var texto = String.valueOf(valor);
            return texto.contains(token) || texto.contains(TokenAleatorio.hash(token)) || texto.contains("@")
                    || texto.contains(NUEVA) || texto.contains("SinDigitos");
        }));
    }
}
