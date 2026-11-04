package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** HU-17: inicio de sesión de dos pasos (POST /api/sesiones, /api/sesiones/mfa y /api/sesiones/mfa/configuracion). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MfaInicioSesionIT.Dobles.class})
class MfaInicioSesionIT {

    private static final String VERIFICACION_EXPIRADA = "Tu verificación expiró o no es válida. Inicia sesión de nuevo.";
    private static final String CODIGO_INVALIDO = "El código no es válido o ya se usó.";
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";

    private static final String CORREO = "ana.mfa@correo.pe";
    private static final String CORREO_ADMIN = "admin.mfa@espacioya.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String ERRADA = "Errada9999";
    private static final String IP_ADMIN = "127.0.0.1";
    private static final List<String> RESPALDOS = List.of("ABCDEFGHIJ", "KLMNOPQRST", "UVWXYZ2345", "67ABCDEFGH",
            "IJKLMNOPQR");
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static String hashContrasena;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private CifradorCampos cifrador;
    @Autowired
    private HuellaCampos huella;
    @Autowired
    private JsonMapper json;

    private String ip;
    private final List<String> secretosVistos = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-11-03T15:00:10Z"));
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM desafio_mfa");
        jdbc.update("DELETE FROM codigo_respaldo");
        jdbc.update("DELETE FROM mfa");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        // Los contadores de fallos viven en memoria: una hora después ya no cuentan. La hora queda en el segundo 10
        // de un paso TOTP, lejos de su borde.
        reloj.avanzar(Duration.ofHours(1));
        ip = siguienteIp();
        secretosVistos.clear();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
    }

    // HU-17 usa 10.17.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.17." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    private Usuario crearAdmin(String correo) {
        return usuarios.save(Usuario.registrarAdmin(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    private Usuario conMfa(Usuario usuario) {
        MfaDePrueba.activar(jdbc, cifrador, usuario.getId(), reloj.instant());
        return usuario;
    }

    private void conRespaldos(Usuario usuario) {
        for (var codigo : RESPALDOS) {
            jdbc.update("INSERT INTO codigo_respaldo (usuario_id, hash_codigo, creado_en) VALUES (?, ?, ?)",
                    usuario.getId(), huella.calcular(codigo), Timestamp.from(reloj.instant()));
        }
    }

    private String codigo() {
        return MfaDePrueba.codigo(reloj.instant());
    }

    private String codigoMalo() {
        var bueno = Integer.parseInt(codigo());
        return String.format("%06d", (bueno + 500_000) % 1_000_000);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder solicitud) {
        var origen = ip;
        return solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
    }

    private ResultActions iniciar(String correo, String contrasena) throws Exception {
        return mvc.perform(desde(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "contrasena", contrasena))));
    }

    private String desafio(String correo) throws Exception {
        return (String) mapa(texto(iniciar(correo, CONTRASENA).andExpect(status().isAccepted()))).get("desafio");
    }

    private ResultActions verificar(String desafio, String codigo) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("desafio", desafio);
        cuerpo.put("codigo", codigo);
        return mvc.perform(desde(post("/api/sesiones/mfa")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions configurar(String desafio) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("desafio", desafio);
        return mvc.perform(desde(post("/api/sesiones/mfa/configuracion")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private String secretoConfigurado(String desafio) throws Exception {
        var secreto = (String) mapa(texto(configurar(desafio).andExpect(status().isOk()))).get("secreto");
        secretosVistos.add(secreto);
        return secreto;
    }

    private static String codigoDe(String secreto, Instant ahora) {
        return Totp.codigo(Base32.decodificar(secreto), Totp.pasoDe(ahora));
    }

    private ResultActions actual(String token) throws Exception {
        return mvc.perform(desde(get("/api/sesiones/actual")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String texto(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private Map<String, Object> filaDesafio(String desafio) {
        return jdbc.queryForMap("SELECT * FROM desafio_mfa WHERE hash_token = ?", TokenAleatorio.hash(desafio));
    }

    private int intentos(String desafio) {
        return ((Number) filaDesafio(desafio).get("intentos_fallidos")).intValue();
    }

    private Map<String, Object> filaMfa(Usuario usuario) {
        return jdbc.queryForMap("SELECT * FROM mfa WHERE usuario_id = ?", usuario.getId());
    }

    private List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    private static long id(Object valor) {
        return ((Number) valor).longValue();
    }

    private void esperaExpirada(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(VERIFICACION_EXPIRADA))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    private void esperaCodigoInvalido(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(CODIGO_INVALIDO));
    }

    private void fallarDesafio(String correo, int veces) throws Exception {
        var desafio = desafio(correo);
        for (int i = 1; i <= veces; i++) {
            var resultado = verificar(desafio, codigoMalo());
            if (i < 5) {
                esperaCodigoInvalido(resultado);
            } else {
                esperaExpirada(resultado);
            }
        }
    }

    // ── Primer paso ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R1, R4, R58: credenciales de una cuenta con MFA activo → 202 solo con desafio y paso CODIGO, "
            + "no-store y sin sesión")
    void conMfaActivoResponde202() throws Exception {
        conMfa(crearUsuario(CORREO));

        var cuerpo = texto(iniciar(CORREO, CONTRASENA)
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.paso").value("CODIGO")));

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("desafio", "paso");
        assertThat((String) mapa(cuerpo).get("desafio")).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(contar("sesion")).isZero();
    }

    @Test
    @DisplayName("HU-17 R5, R6, R7: el desafío se guarda con +5 min, 0 fallos, abierto y solo su SHA-256; dos inicios "
            + "dan desafíos distintos")
    void guardaDesafio() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        var ahora = reloj.instant();

        var primero = desafio(CORREO);
        var segundo = desafio(CORREO);

        var fila = filaDesafio(primero);
        assertThat(id(fila.get("usuario_id"))).isEqualTo(usuario.getId());
        assertThat(fila.get("paso")).isEqualTo("CODIGO");
        assertThat(instante(fila.get("creado_en"))).isEqualTo(ahora);
        assertThat(instante(fila.get("expira_en"))).isEqualTo(ahora.plus(Duration.ofMinutes(5)));
        assertThat(((Number) fila.get("intentos_fallidos")).intValue()).isZero();
        assertThat(fila.get("cerrado_en")).isNull();
        assertThat((String) fila.get("hash_token")).hasSize(64).isEqualTo(TokenAleatorio.hash(primero));
        assertThat(jdbc.queryForList("SELECT * FROM desafio_mfa")).allSatisfy(otra -> assertThat(otra.values())
                .noneMatch(valor -> String.valueOf(valor).contains(primero) || String.valueOf(valor).contains(segundo)));
        assertThat(segundo).hasSize(43).isNotEqualTo(primero);
        assertThat(contar("desafio_mfa")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-17 R2: un admin sin MFA (sin fila o con configuración pendiente) recibe 202 con paso CONFIGURACION")
    void adminSinMfaRecibeConfiguracion() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        ip = IP_ADMIN;

        iniciar(CORREO_ADMIN, CONTRASENA).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paso").value("CONFIGURACION"));
        jdbc.update("INSERT INTO mfa (usuario_id, secreto, activo, creado_en) VALUES (?, ?, false, ?)", admin.getId(),
                cifrador.cifrar(MfaDePrueba.SECRETO), Timestamp.from(reloj.instant()));
        iniciar(CORREO_ADMIN, CONTRASENA).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paso").value("CONFIGURACION"));

        assertThat(contar("sesion")).isZero();
    }

    @Test
    @DisplayName("HU-17 R3: un usuario final con configuración pendiente recibe el 201 de HU-03 sin desafío")
    void usuarioConPendienteRecibeSesion() throws Exception {
        var usuario = crearUsuario(CORREO);
        jdbc.update("INSERT INTO mfa (usuario_id, secreto, activo, creado_en) VALUES (?, ?, false, ?)",
                usuario.getId(), cifrador.cifrar(MfaDePrueba.SECRETO), Timestamp.from(reloj.instant()));

        var cuerpo = texto(iniciar(CORREO, CONTRASENA).andExpect(status().isCreated()));

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("token", "rol", "aviso");
        assertThat(contar("desafio_mfa")).isZero();
        assertThat(contar("sesion")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-17 R8: con MFA activo una contraseña incorrecta responde el mismo 401 de HU-03 sin desafío")
    void contrasenaIncorrectaConMfa() throws Exception {
        conMfa(crearUsuario(CORREO));
        crearUsuario("sinmfa@correo.pe");
        var generico = texto(iniciar("sinmfa@correo.pe", ERRADA).andExpect(status().isUnauthorized()));

        var cuerpo = texto(iniciar(CORREO, ERRADA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(CREDENCIALES_INVALIDAS)));

        assertThat(cuerpo).isEqualTo(generico);
        assertThat(contar("desafio_mfa")).isZero();
    }

    @Test
    @DisplayName("HU-17 R9: un admin con MFA activo desde una IP no permitida recibe el 401 de HU-06 sin desafío")
    void adminConMfaDesdeIpNoPermitida() throws Exception {
        conMfa(crearAdmin(CORREO_ADMIN));

        iniciar(CORREO_ADMIN, CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(CREDENCIALES_INVALIDAS));

        assertThat(contar("desafio_mfa")).isZero();
        assertThat(auditorias("LOGIN_FALLIDO").getLast().get("detalle")).isEqualTo("motivo=IP_NO_PERMITIDA");
    }

    @Test
    @DisplayName("HU-17 R59: el desafío audita LOGIN_MFA_PENDIENTE con desafio_mfa, su id, la IP, la fecha y el paso")
    void auditaLoginMfaPendiente() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));

        var desafio = desafio(CORREO);

        var pendientes = auditorias("LOGIN_MFA_PENDIENTE");
        assertThat(pendientes).hasSize(1);
        var fila = pendientes.getFirst();
        assertThat(id(fila.get("usuario_id"))).isEqualTo(usuario.getId());
        assertThat(fila.get("entidad")).isEqualTo("desafio_mfa");
        assertThat(id(fila.get("entidad_id"))).isEqualTo(id(filaDesafio(desafio).get("id")));
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(fila.get("detalle")).isEqualTo("paso=CODIGO");
        assertThat(auditorias("LOGIN_EXITOSO")).isEmpty();
    }

    // ── Segundo paso: CA2 ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R10, R12, R14, R58, R60: desafío + TOTP → 201 con token, rol, aviso y codigosRespaldo nulo; "
            + "la sesión sirve y se audita factor=TOTP")
    void totpCreaSesion() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        var desafio = desafio(CORREO);

        var cuerpo = texto(verificar(desafio, codigo())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.aviso").isEmpty())
                .andExpect(jsonPath("$.codigosRespaldo").isEmpty()));

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("token", "rol", "aviso", "codigosRespaldo");
        assertThat(mapa(cuerpo).get("codigosRespaldo")).isNull();
        var token = (String) mapa(cuerpo).get("token");
        var sesion = jdbc.queryForMap("SELECT * FROM sesion WHERE hash_token = ?", TokenAleatorio.hash(token));
        assertThat(instante(sesion.get("expira_en"))).isEqualTo(reloj.instant().plus(Duration.ofMinutes(30)));
        actual(token).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("USUARIO"));
        assertThat(id(filaMfa(usuario).get("ultimo_paso"))).isEqualTo(Totp.pasoDe(reloj.instant()));
        var exito = auditorias("LOGIN_EXITOSO").getFirst();
        assertThat(id(exito.get("usuario_id"))).isEqualTo(usuario.getId());
        assertThat(exito.get("entidad")).isEqualTo("sesion");
        assertThat(id(exito.get("entidad_id"))).isEqualTo(id(sesion.get("id")));
        assertThat(exito.get("ip")).isEqualTo(ip);
        assertThat(exito.get("detalle")).isEqualTo("factor=TOTP");
    }

    @Test
    @DisplayName("HU-17 R11, R16, R17, R60, R63: un respaldo en minúsculas con guion → 201; queda usado, se audita con "
            + "restantes=4 y no sirve otra vez")
    void respaldoCreaSesionUnaVez() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        conRespaldos(usuario);

        verificar(desafio(CORREO), "abcde-fghij")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigosRespaldo").isEmpty());

        var usado = jdbc.queryForMap("SELECT usado_en FROM codigo_respaldo WHERE hash_codigo = ?",
                huella.calcular("ABCDEFGHIJ"));
        assertThat(instante(usado.get("usado_en"))).isEqualTo(reloj.instant());
        assertThat(auditorias("LOGIN_EXITOSO").getFirst().get("detalle")).isEqualTo("factor=RESPALDO");
        var uso = auditorias("MFA_CODIGO_RESPALDO_USADO").getFirst();
        assertThat(id(uso.get("usuario_id"))).isEqualTo(usuario.getId());
        assertThat(uso.get("entidad")).isEqualTo("usuario");
        assertThat(id(uso.get("entidad_id"))).isEqualTo(usuario.getId());
        assertThat(uso.get("detalle")).isEqualTo("contexto=INICIO_SESION;restantes=4");

        esperaCodigoInvalido(verificar(desafio(CORREO), "ABCDE FGHIJ"));
        verificar(desafio(CORREO), "klmno pqrst").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-17 R13, R18: reenviar un desafío ya canjeado responde verificación expirada sin crear otra sesión")
    void desafioDeUnSoloUso() throws Exception {
        conMfa(crearUsuario(CORREO));
        var desafio = desafio(CORREO);
        verificar(desafio, codigo()).andExpect(status().isCreated());
        assertThat(instante(filaDesafio(desafio).get("cerrado_en"))).isEqualTo(reloj.instant());
        reloj.avanzar(Duration.ofSeconds(30));

        esperaExpirada(verificar(desafio, codigo()));

        assertThat(contar("sesion")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-17 R14, R15: el mismo código TOTP en dos desafíos solo sirve una vez; el del paso siguiente sí")
    void totpNoSeReutiliza() throws Exception {
        conMfa(crearUsuario(CORREO));
        var codigo = codigo();
        verificar(desafio(CORREO), codigo).andExpect(status().isCreated());

        esperaCodigoInvalido(verificar(desafio(CORREO), codigo));

        reloj.avanzar(Duration.ofSeconds(30));
        verificar(desafio(CORREO), codigo()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-17 R18, R49: desafío inexistente, vencido, cerrado, de cuenta suspendida o sin MFA activo → 401 "
            + "sin contar fallos")
    void desafiosNoVigentes() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));

        esperaExpirada(verificar(TokenAleatorio.generar(), codigo()));

        var vencido = desafio(CORREO);
        reloj.avanzar(Duration.ofMinutes(5));
        esperaExpirada(verificar(vencido, codigo()));
        assertThat(intentos(vencido)).isZero();

        var cerrado = desafio(CORREO);
        verificar(cerrado, codigo()).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofSeconds(30));
        esperaExpirada(verificar(cerrado, codigo()));

        var suspendida = desafio(CORREO);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", usuario.getId());
        esperaExpirada(verificar(suspendida, codigo()));
        jdbc.update("UPDATE usuario SET estado = 'ACTIVA' WHERE id = ?", usuario.getId());

        var sinMfa = desafio(CORREO);
        jdbc.update("UPDATE mfa SET activo = false, activado_en = NULL WHERE usuario_id = ?", usuario.getId());
        esperaExpirada(verificar(sinMfa, codigo()));
        assertThat(intentos(sinMfa)).isZero();

        assertThat(auditorias("MFA_CODIGO_FALLIDO")).isEmpty();
        assertThat(contar("sesion")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-17 R18: un desafío CODIGO no sirve en POST /api/sesiones/mfa/configuracion")
    void desafioCodigoEnConfiguracion() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        var secretoAntes = filaMfa(usuario).get("secreto");

        esperaExpirada(configurar(desafio(CORREO)));

        assertThat(filaMfa(usuario).get("secreto")).isEqualTo(secretoAntes);
    }

    @Test
    @DisplayName("HU-17 R19, R20, R21, R45, R64: 4 códigos incorrectos → 400 en el campo codigo; el 5.º cierra el desafío "
            + "con 401; cada uno se audita")
    void cincoFallosCierranElDesafio() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        var desafio = desafio(CORREO);

        for (int i = 1; i <= 4; i++) {
            esperaCodigoInvalido(verificar(desafio, codigoMalo()));
            assertThat(intentos(desafio)).isEqualTo(i);
        }
        esperaExpirada(verificar(desafio, codigoMalo()));

        assertThat(intentos(desafio)).isEqualTo(5);
        assertThat(instante(filaDesafio(desafio).get("cerrado_en"))).isEqualTo(reloj.instant());
        esperaExpirada(verificar(desafio, codigo()));
        assertThat(contar("sesion")).isZero();
        var fallos = auditorias("MFA_CODIGO_FALLIDO");
        assertThat(fallos).hasSize(5).allSatisfy(fila -> {
            assertThat(id(fila.get("usuario_id"))).isEqualTo(usuario.getId());
            assertThat(fila.get("entidad")).isEqualTo("usuario");
            assertThat(id(fila.get("entidad_id"))).isEqualTo(usuario.getId());
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle")).isEqualTo("contexto=INICIO_SESION");
        });
    }

    @Test
    @DisplayName("HU-17 R22, R49: desafío o código vacíos o demasiado largos → 400 por campo sin contar fallos")
    void solicitudesMalFormadas() throws Exception {
        conMfa(crearUsuario(CORREO));
        var desafio = desafio(CORREO);

        verificar("", codigo()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("desafio"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Inicia sesión de nuevo."));
        verificar("a".repeat(65), codigo()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("desafio"));
        verificar(desafio, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa el código de verificación."));
        for (int i = 0; i < 10; i++) {
            verificar(desafio, "1".repeat(21)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("codigo"));
        }

        assertThat(intentos(desafio)).isZero();
        assertThat(auditorias("MFA_CODIGO_FALLIDO")).isEmpty();
        verificar(desafio, codigo()).andExpect(status().isCreated());
    }

    // ── Límite por cuenta ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R46, R65: tras 10 fallos en 15 min (dos desafíos) un código bueno responde 429, se audita "
            + "MFA_BLOQUEADO y el desafío no suma")
    void diezFallosBloquean() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        fallarDesafio(CORREO, 5);
        fallarDesafio(CORREO, 5);
        var tercero = desafio(CORREO);

        verificar(tercero, codigo())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(intentos(tercero)).isZero();
        assertThat(filaDesafio(tercero).get("cerrado_en")).isNull();
        assertThat(filaMfa(usuario).get("ultimo_paso")).isNull();
        assertThat(contar("sesion")).isZero();
        var bloqueo = auditorias("MFA_BLOQUEADO").getFirst();
        assertThat(id(bloqueo.get("usuario_id"))).isEqualTo(usuario.getId());
        assertThat(bloqueo.get("entidad")).isEqualTo("usuario");
        assertThat(bloqueo.get("ip")).isEqualTo(ip);
        assertThat(instante(bloqueo.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(auditorias("MFA_CODIGO_FALLIDO")).hasSize(10);
    }

    @Test
    @DisplayName("HU-17 R47: 15 minutos después de los fallos el código vuelve a procesarse")
    void bloqueoCaducaA15Minutos() throws Exception {
        conMfa(crearUsuario(CORREO));
        fallarDesafio(CORREO, 5);
        fallarDesafio(CORREO, 5);
        reloj.avanzar(Duration.ofMinutes(15));

        verificar(desafio(CORREO), codigo()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-17 R48: un código aceptado reinicia el contador: 9 fallos, un acierto y 9 fallos más no bloquean")
    void aciertoReiniciaContador() throws Exception {
        conMfa(crearUsuario(CORREO));
        fallarDesafio(CORREO, 5);
        var segundo = desafio(CORREO);
        for (int i = 0; i < 4; i++) {
            esperaCodigoInvalido(verificar(segundo, codigoMalo()));
        }
        verificar(segundo, codigo()).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofSeconds(30));
        fallarDesafio(CORREO, 5);
        var cuarto = desafio(CORREO);
        for (int i = 0; i < 4; i++) {
            esperaCodigoInvalido(verificar(cuarto, codigoMalo()));
        }

        verificar(cuarto, codigo()).andExpect(status().isCreated());
    }

    // ── Configuración obligatoria del admin: CA1, CA3 ───────────────────

    @Test
    @DisplayName("HU-17 R23, R24, R25, R26, R36, R37, R58, R61: el admin configura MFA en su primer inicio de sesión y "
            + "recibe la sesión con 5 códigos de respaldo")
    void adminConfiguraMfaAlIniciarSesion() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        ip = IP_ADMIN;
        var desafio = desafio(CORREO_ADMIN);

        var configuracion = texto(configurar(desafio)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))));
        assertThat(mapa(configuracion).keySet()).containsExactlyInAnyOrder("secreto", "uri");
        var secreto = (String) mapa(configuracion).get("secreto");
        assertThat(secreto).matches("^[A-Z2-7]{32}$");
        assertThat(mapa(configuracion).get("uri")).isEqualTo("otpauth://totp/EspacioYa:admin.mfa%40espacioya.pe"
                + "?secret=" + secreto + "&issuer=EspacioYa&algorithm=SHA1&digits=6&period=30");
        assertThat(filaMfa(admin).get("activo")).isEqualTo(false);

        var cuerpo = texto(verificar(desafio, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.aviso").isEmpty()));

        @SuppressWarnings("unchecked")
        var codigos = (List<String>) mapa(cuerpo).get("codigosRespaldo");
        assertThat(codigos).hasSize(5).doesNotHaveDuplicates()
                .allSatisfy(codigo -> assertThat(codigo).matches("^[A-Z2-7]{5}-[A-Z2-7]{5}$"));
        var mfa = filaMfa(admin);
        assertThat(mfa.get("activo")).isEqualTo(true);
        assertThat(instante(mfa.get("activado_en"))).isEqualTo(reloj.instant());
        var huellas = jdbc.queryForList("SELECT hash_codigo FROM codigo_respaldo WHERE usuario_id = ?", String.class,
                admin.getId());
        assertThat(huellas).containsExactlyInAnyOrderElementsOf(
                codigos.stream().map(codigo -> huella.calcular(codigo.replace("-", ""))).toList());
        actual((String) mapa(cuerpo).get("token")).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
        var activado = auditorias("MFA_ACTIVADO").getFirst();
        assertThat(id(activado.get("usuario_id"))).isEqualTo(admin.getId());
        assertThat(activado.get("entidad")).isEqualTo("usuario");
        assertThat(id(activado.get("entidad_id"))).isEqualTo(admin.getId());
        assertThat(activado.get("ip")).isEqualTo(IP_ADMIN);
        assertThat(activado.get("detalle")).isEqualTo("contexto=INICIO_SESION");

        reloj.avanzar(Duration.ofSeconds(30));
        iniciar(CORREO_ADMIN, CONTRASENA).andExpect(status().isAccepted()).andExpect(jsonPath("$.paso").value("CODIGO"));
    }

    @Test
    @DisplayName("HU-17 R24, R54: una segunda configuración reemplaza el secreto pendiente por otro distinto")
    void segundaConfiguracionReemplazaSecreto() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        ip = IP_ADMIN;
        var desafio = desafio(CORREO_ADMIN);
        var primero = secretoConfigurado(desafio);
        var cifradoPrimero = filaMfa(admin).get("secreto");

        var segundo = secretoConfigurado(desafio);

        assertThat(segundo).isNotEqualTo(primero);
        assertThat(filaMfa(admin).get("secreto")).isNotEqualTo(cifradoPrimero);
        assertThat(contar("mfa")).isEqualTo(1);
        esperaCodigoInvalido(verificar(desafio, codigoDe(primero, reloj.instant())));
        verificar(desafio, codigoDe(segundo, reloj.instant())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-17 R27: en el paso CONFIGURACION sin pendiente, con un código erróneo o con un respaldo → 400 sin "
            + "activar MFA")
    void configuracionRechazada() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        ip = IP_ADMIN;
        var desafio = desafio(CORREO_ADMIN);

        esperaCodigoInvalido(verificar(desafio, codigo()));
        var secreto = secretoConfigurado(desafio);
        var bueno = Integer.parseInt(codigoDe(secreto, reloj.instant()));
        esperaCodigoInvalido(verificar(desafio, String.format("%06d", (bueno + 1) % 1_000_000)));
        esperaCodigoInvalido(verificar(desafio, "ABCDE-FGHIJ"));

        assertThat(intentos(desafio)).isEqualTo(3);
        assertThat(filaMfa(admin).get("activo")).isEqualTo(false);
        assertThat(contar("codigo_respaldo")).isZero();
        assertThat(contar("sesion")).isZero();
    }

    @Test
    @DisplayName("HU-17 R35: al activar MFA en el inicio de sesión se revocan las sesiones previas del admin")
    void activarRevocaSesionesPrevias() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        var vieja = TokenAleatorio.generar();
        jdbc.update("INSERT INTO sesion (usuario_id, hash_token, creada_en, expira_en, revocada) VALUES (?, ?, ?, ?, "
                + "FALSE)", admin.getId(), TokenAleatorio.hash(vieja), Timestamp.from(reloj.instant()),
                Timestamp.from(reloj.instant().plus(Duration.ofMinutes(30))));
        ip = IP_ADMIN;
        var desafio = desafio(CORREO_ADMIN);
        var secreto = secretoConfigurado(desafio);

        var token = (String) mapa(texto(verificar(desafio, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isCreated()))).get("token");

        assertThat(jdbc.queryForObject("SELECT revocada FROM sesion WHERE hash_token = ?", Boolean.class,
                TokenAleatorio.hash(vieja))).isTrue();
        actual(vieja).andExpect(status().isUnauthorized());
        actual(token).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-17 R28: la sesión vigente de un admin sin MFA activo responde 401; con MFA activo, 200")
    void sesionDeAdminSinMfaRechazada() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        var token = TokenAleatorio.generar();
        jdbc.update("INSERT INTO sesion (usuario_id, hash_token, creada_en, expira_en, revocada) VALUES (?, ?, ?, ?, "
                + "FALSE)", admin.getId(), TokenAleatorio.hash(token), Timestamp.from(reloj.instant()),
                Timestamp.from(reloj.instant().plus(Duration.ofMinutes(30))));

        actual(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        mvc.perform(desde(get("/api/admin/cuentas")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .with(peticion -> {
                    peticion.setRemoteAddr(IP_ADMIN);
                    return peticion;
                })).andExpect(status().isUnauthorized());

        conMfa(admin);
        actual(token).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    // ── Concurrencia (DT-11) ─────────────────────────────────────────────

    // Lanza las dos solicitudes a la vez (mismo patrón que HU-04 R22) y devuelve sus respuestas.
    private List<MvcResult> aLaVez(Callable<ResultActions> primera, Callable<ResultActions> segunda) throws Exception {
        var listos = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(() -> {
                listos.await();
                return primera.call().andReturn();
            });
            var b = hilos.submit(() -> {
                listos.await();
                return segunda.call().andReturn();
            });
            listos.countDown();
            return List.of(a.get(), b.get());
        }
    }

    private static List<Integer> estados(List<MvcResult> resultados) {
        return resultados.stream().map(resultado -> resultado.getResponse().getStatus()).toList();
    }

    private MvcResult conEstado(List<MvcResult> resultados, int estado) {
        return resultados.stream().filter(resultado -> resultado.getResponse().getStatus() == estado).findFirst()
                .orElseThrow();
    }

    private Map<String, Object> cuerpo(MvcResult resultado) throws Exception {
        return mapa(resultado.getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private void esCodigoInvalido(MvcResult resultado) throws Exception {
        var errores = (List<Map<String, Object>>) cuerpo(resultado).get("errores");
        assertThat(cuerpo(resultado).get("mensaje")).isEqualTo(MENSAJE_VALIDACION);
        assertThat(errores).singleElement().satisfies(error -> {
            assertThat(error.get("campo")).isEqualTo("codigo");
            assertThat(error.get("mensaje")).isEqualTo(CODIGO_INVALIDO);
        });
    }

    @Test
    @DisplayName("HU-17 R13: dos envíos simultáneos del mismo desafío y el mismo TOTP → un 201 y un 401; una sola sesión")
    void desafioSimultaneoSoloUnoGana() throws Exception {
        conMfa(crearUsuario(CORREO));
        var desafio = desafio(CORREO);
        var codigo = codigo();

        var resultados = aLaVez(() -> verificar(desafio, codigo), () -> verificar(desafio, codigo));

        assertThat(estados(resultados)).containsExactlyInAnyOrder(201, 401);
        assertThat(cuerpo(conEstado(resultados, 401)).get("mensaje")).isEqualTo(VERIFICACION_EXPIRADA);
        assertThat(contar("sesion")).isEqualTo(1);
        assertThat(instante(filaDesafio(desafio).get("cerrado_en"))).isEqualTo(reloj.instant());
        assertThat(auditorias("LOGIN_EXITOSO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-17 R15: el mismo TOTP enviado a la vez en dos desafíos de la cuenta → un 201 y un 400 en codigo; "
            + "una sola sesión")
    void totpSimultaneoNoSeReutiliza() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        var primero = desafio(CORREO);
        var segundo = desafio(CORREO);
        var codigo = codigo();

        var resultados = aLaVez(() -> verificar(primero, codigo), () -> verificar(segundo, codigo));

        assertThat(estados(resultados)).containsExactlyInAnyOrder(201, 400);
        esCodigoInvalido(conEstado(resultados, 400));
        assertThat(contar("sesion")).isEqualTo(1);
        assertThat(id(filaMfa(usuario).get("ultimo_paso"))).isEqualTo(Totp.pasoDe(reloj.instant()));
        assertThat(auditorias("LOGIN_EXITOSO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-17 R16: el mismo código de respaldo enviado a la vez en dos desafíos → un solo 201; queda usado "
            + "y se audita una vez")
    void respaldoSimultaneoUnSoloUso() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        conRespaldos(usuario);
        var primero = desafio(CORREO);
        var segundo = desafio(CORREO);

        var resultados = aLaVez(() -> verificar(primero, "ABCDEFGHIJ"), () -> verificar(segundo, "ABCDEFGHIJ"));

        assertThat(estados(resultados)).containsExactlyInAnyOrder(201, 400);
        esCodigoInvalido(conEstado(resultados, 400));
        assertThat(contar("sesion")).isEqualTo(1);
        var usado = jdbc.queryForMap("SELECT usado_en FROM codigo_respaldo WHERE hash_codigo = ?",
                huella.calcular("ABCDEFGHIJ"));
        assertThat(instante(usado.get("usado_en"))).isEqualTo(reloj.instant());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_respaldo WHERE usado_en IS NOT NULL",
                Integer.class)).isEqualTo(1);
        assertThat(auditorias("MFA_CODIGO_RESPALDO_USADO")).singleElement()
                .satisfies(fila -> assertThat(fila.get("detalle")).isEqualTo("contexto=INICIO_SESION;restantes=4"));
    }

    @Test
    @DisplayName("HU-17 R24: dos configuraciones simultáneas de un admin sin fila de mfa → ambas 200 y una sola fila "
            + "con uno de los dos secretos")
    void configuracionSimultaneaSinChoque() throws Exception {
        var admin = crearAdmin(CORREO_ADMIN);
        ip = IP_ADMIN;
        var primero = desafio(CORREO_ADMIN);
        var segundo = desafio(CORREO_ADMIN);

        var resultados = aLaVez(() -> configurar(primero), () -> configurar(segundo));

        assertThat(estados(resultados)).containsExactly(200, 200);
        var secretos = new ArrayList<String>();
        for (var resultado : resultados) {
            secretos.add((String) cuerpo(resultado).get("secreto"));
        }
        assertThat(secretos).doesNotHaveDuplicates();
        assertThat(contar("mfa")).isEqualTo(1);
        var guardado = cifrador.descifrar((String) filaMfa(admin).get("secreto"));
        assertThat(guardado).isIn(secretos);
        assertThat(filaMfa(admin).get("activo")).isEqualTo(false);
    }

    // ── Auditoría sin secretos ───────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R66: ningún detalle de auditoría contiene el desafío, el código, el secreto, el token ni "
            + "los respaldos")
    void auditoriaSinSecretos() throws Exception {
        var usuario = conMfa(crearUsuario(CORREO));
        conRespaldos(usuario);
        var sensibles = new ArrayList<String>();
        var primero = desafio(CORREO);
        var malo = codigoMalo();
        sensibles.addAll(List.of(primero, malo));
        verificar(primero, malo).andExpect(status().isBadRequest());
        var bueno = codigo();
        sensibles.add(bueno);
        sensibles.add((String) mapa(texto(verificar(primero, bueno).andExpect(status().isCreated()))).get("token"));
        var segundo = desafio(CORREO);
        sensibles.add(segundo);
        verificar(segundo, "ABCDE-FGHIJ").andExpect(status().isCreated());
        sensibles.addAll(RESPALDOS);
        sensibles.add(MfaDePrueba.SECRETO);

        var admin = crearAdmin(CORREO_ADMIN);
        ip = IP_ADMIN;
        var desafioAdmin = desafio(CORREO_ADMIN);
        sensibles.add(desafioAdmin);
        var secreto = secretoConfigurado(desafioAdmin);
        sensibles.add(secreto);
        var cuerpo = mapa(texto(verificar(desafioAdmin, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isCreated())));
        sensibles.add((String) cuerpo.get("token"));
        @SuppressWarnings("unchecked")
        var codigos = (List<String>) cuerpo.get("codigosRespaldo");
        codigos.forEach(codigo -> sensibles.addAll(List.of(codigo, codigo.replace("-", ""))));

        assertThat(admin.getId()).isNotNull();
        var detalles = jdbc.queryForList("SELECT detalle FROM auditoria", String.class);
        assertThat(detalles).isNotEmpty().allSatisfy(detalle -> {
            for (var sensible : sensibles) {
                assertThat(String.valueOf(detalle)).doesNotContain(sensible);
            }
        });
        assertThat(secretosVistos).hasSize(1);
    }
}
