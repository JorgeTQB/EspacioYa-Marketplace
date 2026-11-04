package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** HU-17: MFA desde la cuenta con sesión (GET /api/mfa, configuración, activación y desactivación). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MfaControllerIT.Dobles.class})
class MfaControllerIT {

    private static final String CODIGO_INVALIDO = "El código no es válido o ya se usó.";
    private static final String YA_ACTIVO = "La verificación en dos pasos ya está activa en tu cuenta.";
    private static final String SIN_CONFIGURACION = "Primero genera tu clave de configuración.";
    private static final String NO_ACTIVO = "La verificación en dos pasos no está activa en tu cuenta.";
    private static final String RECHAZADA = "La contraseña o el código no son correctos.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";

    private static final String CORREO = "ana+mfa@correo.pe";
    private static final String CORREO_ADMIN = "admin.cuenta@espacioya.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String ERRADA = "Errada9999";
    private static final String IP_ADMIN = "127.0.0.1";
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
        reloj.avanzar(Duration.ofHours(1));
        ip = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
    }

    // HU-17 usa 10.17.x.x; MfaInicioSesionIT empieza desde abajo y esta clase desde 10.17.200.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.17." + (200 + n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    private Map<String, Object> filaMfa(Usuario usuario) {
        return jdbc.queryForMap("SELECT * FROM mfa WHERE usuario_id = ?", usuario.getId());
    }

    private int contar(String consulta, Object... argumentos) {
        return jdbc.queryForObject(consulta, Integer.class, argumentos);
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

    private static String codigoDe(String secreto, Instant ahora) {
        return Totp.codigo(Base32.decodificar(secreto), Totp.pasoDe(ahora));
    }

    private static String otroCodigo(String codigo) {
        return String.format("%06d", (Integer.parseInt(codigo) + 500_000) % 1_000_000);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder solicitud) {
        var origen = ip;
        return solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder solicitud, String token) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return desde(solicitud);
    }

    private ResultActions iniciar(String correo) throws Exception {
        return mvc.perform(desde(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "contrasena", CONTRASENA))));
    }

    private String token(String correo) throws Exception {
        return (String) mapa(texto(iniciar(correo).andExpect(status().isCreated()))).get("token");
    }

    private ResultActions estado(String token) throws Exception {
        return mvc.perform(con(get("/api/mfa"), token));
    }

    private ResultActions configurar(String token) throws Exception {
        return mvc.perform(con(post("/api/mfa/configuracion"), token));
    }

    private ResultActions activar(String token, String codigo) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("codigo", codigo);
        return mvc.perform(con(post("/api/mfa/activacion"), token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions desactivar(String token, String contrasena, String codigo) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("contrasena", contrasena);
        cuerpo.put("codigo", codigo);
        return mvc.perform(con(post("/api/mfa/desactivacion"), token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private String secreto(String token) throws Exception {
        return (String) mapa(texto(configurar(token).andExpect(status().isOk()))).get("secreto");
    }

    @SuppressWarnings("unchecked")
    private List<String> activarConSecreto(String token, String secreto) throws Exception {
        return (List<String>) mapa(texto(activar(token, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isOk()))).get("codigos");
    }

    // Usuario con MFA activo por la API; deja el reloj en el paso siguiente para que el próximo código sirva.
    private String usuarioConMfa() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);
        activarConSecreto(token, secreto(token));
        reloj.avanzar(Duration.ofSeconds(30));
        return token;
    }

    private String texto(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    private void esperaRechazada(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(RECHAZADA))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    // ── Estado ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R39, R58: sin MFA el estado es inactivo, 0 restantes y no desactivable, con no-store")
    void estadoSinMfa() throws Exception {
        crearUsuario(CORREO);

        var cuerpo = texto(estado(token(CORREO))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.codigosRespaldoRestantes").value(0))
                .andExpect(jsonPath("$.desactivable").value(false)));

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("activo", "codigosRespaldoRestantes",
                "desactivable");
    }

    @Test
    @DisplayName("HU-17 R44: sin sesión vigente /api/mfa y sus subrutas responden 401 de sesión inválida")
    void sinSesion() throws Exception {
        for (var resultado : List.of(estado(null), configurar(null), activar(null, "123456"),
                desactivar(null, CONTRASENA, "123456"), estado("tokenInventadoDeCuarentaYTresCaracteres_xyz"))) {
            resultado.andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        }
        assertThat(contar("SELECT count(*) FROM mfa")).isZero();
    }

    // ── Configuración y activación: CA2, CA3 ─────────────────────────────

    @Test
    @DisplayName("HU-17 R29, R50, R55, R58: configurar devuelve solo secreto y uri; el secreto se guarda cifrado")
    void configurarGuardaSecretoCifrado() throws Exception {
        var usuario = crearUsuario(CORREO);

        var cuerpo = texto(configurar(token(CORREO))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))));

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("secreto", "uri");
        var secreto = (String) mapa(cuerpo).get("secreto");
        assertThat(secreto).matches("^[A-Z2-7]{32}$");
        assertThat(mapa(cuerpo).get("uri")).isEqualTo("otpauth://totp/EspacioYa:ana%2Bmfa%40correo.pe?secret="
                + secreto + "&issuer=EspacioYa&algorithm=SHA1&digits=6&period=30");
        var fila = filaMfa(usuario);
        assertThat(fila.get("activo")).isEqualTo(false);
        assertThat((String) fila.get("secreto")).startsWith("v1:").doesNotContain(secreto);
        assertThat(cifrador.descifrar((String) fila.get("secreto"))).isEqualTo(secreto);
        assertThat(fila.values()).noneMatch(valor -> String.valueOf(valor).contains(secreto));
    }

    @Test
    @DisplayName("HU-17 R24, R54: configurar dos veces reemplaza el secreto pendiente por otro distinto")
    void configurarDosVeces() throws Exception {
        var usuario = crearUsuario(CORREO);
        var token = token(CORREO);

        var primero = secreto(token);
        var segundo = secreto(token);

        assertThat(segundo).isNotEqualTo(primero);
        assertThat(cifrador.descifrar((String) filaMfa(usuario).get("secreto"))).isEqualTo(segundo);
        assertThat(contar("SELECT count(*) FROM mfa")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-17 R31, R32, R36, R37, R38, R56, R58, R61: activar con el primer código deja MFA activo y "
            + "devuelve solo los 5 códigos, guardados como HMAC")
    void activarDevuelveCodigos() throws Exception {
        var usuario = crearUsuario(CORREO);
        var token = token(CORREO);
        var secreto = secreto(token);

        var cuerpo = texto(activar(token, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store"))));

        assertThat(mapa(cuerpo).keySet()).containsExactly("codigos");
        @SuppressWarnings("unchecked")
        var codigos = (List<String>) mapa(cuerpo).get("codigos");
        assertThat(codigos).hasSize(5).doesNotHaveDuplicates()
                .allSatisfy(codigo -> assertThat(codigo).matches("^[A-Z2-7]{5}-[A-Z2-7]{5}$"));
        var fila = filaMfa(usuario);
        assertThat(fila.get("activo")).isEqualTo(true);
        assertThat(instante(fila.get("activado_en"))).isEqualTo(reloj.instant());
        var guardados = jdbc.queryForList("SELECT * FROM codigo_respaldo WHERE usuario_id = ?", usuario.getId());
        assertThat(guardados).extracting(codigo -> codigo.get("hash_codigo")).containsExactlyInAnyOrderElementsOf(
                codigos.stream().map(codigo -> huella.calcular(codigo.replace("-", ""))).toList());
        for (var codigo : codigos) {
            assertThat(guardados).allSatisfy(otra -> assertThat(otra.values()).noneMatch(valor ->
                    String.valueOf(valor).contains(codigo) || String.valueOf(valor).contains(codigo.replace("-", ""))));
        }
        var estado = texto(estado(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.codigosRespaldoRestantes").value(5))
                .andExpect(jsonPath("$.desactivable").value(true)));
        assertThat(estado).doesNotContain(secreto).doesNotContain(codigos.getFirst());
        assertThat(auditorias("MFA_ACTIVADO")).singleElement().satisfies(fila2 -> {
            assertThat(id(fila2.get("usuario_id"))).isEqualTo(usuario.getId());
            assertThat(fila2.get("entidad")).isEqualTo("usuario");
            assertThat(id(fila2.get("entidad_id"))).isEqualTo(usuario.getId());
            assertThat(fila2.get("ip")).isEqualTo(ip);
            assertThat(instante(fila2.get("fecha"))).isEqualTo(reloj.instant());
            assertThat(fila2.get("detalle")).isEqualTo("contexto=CUENTA");
        });
    }

    @Test
    @DisplayName("HU-17 R35: activar revoca las demás sesiones del usuario y conserva la de la solicitud")
    void activarRevocaOtrasSesiones() throws Exception {
        crearUsuario(CORREO);
        var actual = token(CORREO);
        var otra = token(CORREO);

        activarConSecreto(actual, secreto(actual));

        estado(otra).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        estado(actual).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-17 CA2, R1, R11: tras activar, el siguiente inicio de sesión pide el código y acepta un respaldo")
    void siguienteInicioPideCodigo() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);
        var codigos = activarConSecreto(token, secreto(token));

        var desafio = (String) mapa(texto(iniciar(CORREO).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paso").value("CODIGO")))).get("desafio");
        mvc.perform(desde(post("/api/sesiones/mfa")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("desafio", desafio,
                                "codigo", codigos.getFirst().toLowerCase()))))
                .andExpect(status().isCreated());

        estado(token).andExpect(jsonPath("$.codigosRespaldoRestantes").value(4));
    }

    @Test
    @DisplayName("HU-17 R30: con MFA activo, configurar responde 400 MFA ya activo sin cambiar el secreto")
    void configurarYaActivo() throws Exception {
        var token = usuarioConMfa();
        var usuario = usuarios.findByCorreo(CORREO).orElseThrow();
        var antes = filaMfa(usuario).get("secreto");

        configurar(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(YA_ACTIVO))
                .andExpect(jsonPath("$.errores").isEmpty());

        assertThat(filaMfa(usuario).get("secreto")).isEqualTo(antes);
    }

    @Test
    @DisplayName("HU-17 R33, R49: activar sin configuración pendiente (sin fila o ya activo) responde 400 sin contar "
            + "fallo")
    void activarSinPendiente() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);

        for (int i = 0; i < 11; i++) {
            activar(token, "123456").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(SIN_CONFIGURACION))
                    .andExpect(jsonPath("$.errores").isEmpty());
        }

        assertThat(auditorias("MFA_CODIGO_FALLIDO")).isEmpty();
        activarConSecreto(token, secreto(token));
        reloj.avanzar(Duration.ofSeconds(30));
        activar(token, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(SIN_CONFIGURACION));
    }

    @Test
    @DisplayName("HU-17 R34, R45, R64: un código incorrecto responde 400 en el campo codigo, no activa y se audita "
            + "contexto=ACTIVACION")
    void activarCodigoIncorrecto() throws Exception {
        var usuario = crearUsuario(CORREO);
        var token = token(CORREO);
        var secreto = secreto(token);

        activar(token, otroCodigo(codigoDe(secreto, reloj.instant())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(CODIGO_INVALIDO));
        activar(token, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa el código de verificación."));

        assertThat(filaMfa(usuario).get("activo")).isEqualTo(false);
        assertThat(contar("SELECT count(*) FROM codigo_respaldo")).isZero();
        assertThat(auditorias("MFA_CODIGO_FALLIDO")).singleElement()
                .satisfies(fila -> assertThat(fila.get("detalle")).isEqualTo("contexto=ACTIVACION"));
    }

    @Test
    @DisplayName("HU-17 R46: 10 activaciones fallidas bloquean también el código bueno (429)")
    void activacionesFallidasBloquean() throws Exception {
        var usuario = crearUsuario(CORREO);
        var token = token(CORREO);
        var secreto = secreto(token);
        var malo = otroCodigo(codigoDe(secreto, reloj.instant()));
        for (int i = 0; i < 10; i++) {
            activar(token, malo).andExpect(status().isBadRequest());
        }

        activar(token, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(filaMfa(usuario).get("activo")).isEqualTo(false);
        assertThat(auditorias("MFA_BLOQUEADO")).hasSize(1);
    }

    // ── Desactivación ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R40, R62: contraseña y código correctos → 204, se borran MFA y códigos, se audita y el siguiente "
            + "inicio de sesión no pide código")
    void desactivar() throws Exception {
        var token = usuarioConMfa();
        var usuario = usuarios.findByCorreo(CORREO).orElseThrow();

        var secreto = cifrador.descifrar((String) filaMfa(usuario).get("secreto"));

        desactivar(token, CONTRASENA, codigoDe(secreto, reloj.instant()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(contar("SELECT count(*) FROM mfa WHERE usuario_id = ?", usuario.getId())).isZero();
        assertThat(contar("SELECT count(*) FROM codigo_respaldo WHERE usuario_id = ?", usuario.getId())).isZero();
        assertThat(auditorias("MFA_DESACTIVADO")).singleElement().satisfies(fila -> {
            assertThat(id(fila.get("usuario_id"))).isEqualTo(usuario.getId());
            assertThat(fila.get("entidad")).isEqualTo("usuario");
            assertThat(id(fila.get("entidad_id"))).isEqualTo(usuario.getId());
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        });
        iniciar(CORREO).andExpect(status().isCreated());
        estado(token).andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("HU-17 R40, R63: un código de respaldo también desactiva y se audita contexto=DESACTIVACION")
    void desactivarConRespaldo() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);
        var codigos = activarConSecreto(token, secreto(token));

        desactivar(token, CONTRASENA, codigos.get(2)).andExpect(status().isNoContent());

        assertThat(auditorias("MFA_CODIGO_RESPALDO_USADO")).singleElement()
                .satisfies(fila -> assertThat(fila.get("detalle")).isEqualTo("contexto=DESACTIVACION;restantes=4"));
    }

    @Test
    @DisplayName("HU-17 R41, R45: contraseña incorrecta o código incorrecto → el mismo 400, sin desactivar")
    void desactivarRechazada() throws Exception {
        var token = usuarioConMfa();
        var usuario = usuarios.findByCorreo(CORREO).orElseThrow();
        var secreto = cifrador.descifrar((String) filaMfa(usuario).get("secreto"));
        var bueno = codigoDe(secreto, reloj.instant());

        var primera = texto(desactivar(token, ERRADA, bueno));
        var segunda = texto(desactivar(token, CONTRASENA, otroCodigo(bueno)));
        esperaRechazada(desactivar(token, ERRADA, "ABCDE-FGHIJ"));

        assertThat(primera).isEqualTo(segunda).contains(RECHAZADA);
        assertThat(filaMfa(usuario).get("activo")).isEqualTo(true);
        assertThat(auditorias("MFA_CODIGO_FALLIDO")).hasSize(3)
                .allSatisfy(fila -> assertThat(fila.get("detalle")).isEqualTo("contexto=DESACTIVACION"));
    }

    @Test
    @DisplayName("HU-17 R42: un admin que intenta desactivar recibe 403 y su MFA no cambia")
    void adminNoDesactiva() throws Exception {
        var admin = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hashContrasena, "2026-10", reloj.instant()));
        MfaDePrueba.activar(jdbc, cifrador, admin.getId(), reloj.instant());
        ip = IP_ADMIN;
        var token = MfaDePrueba.iniciarSesion(mvc, jdbc, CORREO_ADMIN, CONTRASENA, IP_ADMIN, reloj.instant());
        reloj.avanzar(Duration.ofSeconds(30));

        desactivar(token, CONTRASENA, MfaDePrueba.codigo(reloj.instant()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        assertThat(filaMfa(admin).get("activo")).isEqualTo(true);
        assertThat(filaMfa(admin).get("ultimo_paso")).isNotNull()
                .isEqualTo(Totp.pasoDe(reloj.instant().minusSeconds(30)));
        estado(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.desactivable").value(false));
    }

    @Test
    @DisplayName("HU-17 R43, R49: sin MFA activo desactivar responde 400 MFA no activo sin contar fallo")
    void desactivarSinMfa() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);

        desactivar(token, CONTRASENA, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(NO_ACTIVO))
                .andExpect(jsonPath("$.errores").isEmpty());

        assertThat(auditorias("MFA_CODIGO_FALLIDO")).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R45, R46: los fallos de desactivación cuentan para el límite del inicio de sesión")
    void fallosDeDesactivacionBloqueanElLogin() throws Exception {
        var token = usuarioConMfa();
        var usuario = usuarios.findByCorreo(CORREO).orElseThrow();
        var bueno = codigoDe(cifrador.descifrar((String) filaMfa(usuario).get("secreto")), reloj.instant());
        for (int i = 0; i < 10; i++) {
            esperaRechazada(desactivar(token, ERRADA, otroCodigo(bueno)));
        }
        desactivar(token, CONTRASENA, bueno).andExpect(status().isTooManyRequests());

        var desafio = (String) mapa(texto(iniciar(CORREO).andExpect(status().isAccepted()))).get("desafio");
        mvc.perform(desde(post("/api/sesiones/mfa")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("desafio", desafio, "codigo", bueno))))
                .andExpect(status().isTooManyRequests());
        assertThat(filaMfa(usuario).get("activo")).isEqualTo(true);
    }

    // ── Auditoría sin secretos ───────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R66: ningún detalle de auditoría contiene el secreto, la URI, el código, la contraseña ni los "
            + "respaldos")
    void auditoriaSinSecretos() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);
        var configuracion = mapa(texto(configurar(token).andExpect(status().isOk())));
        var secreto = (String) configuracion.get("secreto");
        var bueno = codigoDe(secreto, reloj.instant());
        activar(token, otroCodigo(bueno)).andExpect(status().isBadRequest());
        @SuppressWarnings("unchecked")
        var codigos = (List<String>) mapa(texto(activar(token, bueno).andExpect(status().isOk()))).get("codigos");
        desactivar(token, ERRADA, codigos.getFirst()).andExpect(status().isBadRequest());
        desactivar(token, CONTRASENA, codigos.get(1)).andExpect(status().isNoContent());

        var sensibles = new ArrayList<>(List.of(secreto, (String) configuracion.get("uri"), bueno, otroCodigo(bueno),
                CONTRASENA, ERRADA, token, CORREO));
        codigos.forEach(codigo -> sensibles.addAll(List.of(codigo, codigo.replace("-", ""))));
        var detalles = jdbc.queryForList("SELECT detalle FROM auditoria", String.class);
        assertThat(detalles).isNotEmpty().allSatisfy(detalle -> {
            for (var sensible : sensibles) {
                assertThat(String.valueOf(detalle)).doesNotContain(sensible);
            }
        });
    }
}
