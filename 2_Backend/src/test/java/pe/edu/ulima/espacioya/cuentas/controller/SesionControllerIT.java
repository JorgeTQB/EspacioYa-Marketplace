package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SesionControllerIT.Dobles.class})
class SesionControllerIT {

    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    private static final String AVISO = "Para publicar tus espacios, primero debes verificar tu identidad.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String FRONTEND = "http://localhost:5173";

    private static final String CORREO = "ana@correo.pe";
    private static final String OTRO_CORREO = "beto@correo.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String ERRADA = "Errada9999";
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
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JsonMapper json;

    private String ip;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        // El contador de fallos vive en memoria y se comparte entre tests: una hora después ya no cuenta nada.
        reloj.avanzar(Duration.ofHours(1));
        ip = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
    }

    // HU-01 usa 10.1.x.x y HU-02 10.2.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.3." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    private Usuario crearArrendador(String correo) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, "Ana Pérez", "987654321",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970", "c".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        return usuario;
    }

    private static String credenciales(String correo, String contrasena) {
        return "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}";
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder solicitud) {
        var origen = ip;
        return solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
    }

    private ResultActions iniciar(String cuerpo) throws Exception {
        return mvc.perform(desde(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions iniciar(String correo, String contrasena) throws Exception {
        return iniciar(credenciales(correo, contrasena));
    }

    private String token(String correo) throws Exception {
        var cuerpo = iniciar(correo, CONTRASENA).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    private ResultActions actual(String token) throws Exception {
        return mvc.perform(desde(get("/api/sesiones/actual")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions cerrar(String token) throws Exception {
        return mvc.perform(desde(delete("/api/sesiones/actual"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void fallos(String correo, int veces) throws Exception {
        for (int i = 0; i < veces; i++) {
            iniciar(correo, ERRADA).andExpect(status().isUnauthorized());
        }
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private int sesiones() {
        return jdbc.queryForObject("SELECT count(*) FROM sesion", Integer.class);
    }

    private Map<String, Object> sesionDe(String token) {
        return jdbc.queryForMap("SELECT * FROM sesion WHERE hash_token = ?", TokenAleatorio.hash(token));
    }

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    private Instant dentroDeMediaHora() {
        return reloj.instant().plus(Duration.ofMinutes(30));
    }

    private List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    private void esperaSesionInvalida(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    // ── CA1: inicio de sesión exitoso ────────────────────────────────────

    @Test
    @DisplayName("HU-03 R1, R7: credenciales correctas (correo con mayúsculas y espacios) → 201 con token, rol y aviso")
    void loginCorrectoResponde201ConCamposMinimos() throws Exception {
        var usuario = crearUsuario(CORREO);

        var cuerpo = iniciar("  ANA@Correo.PE ", CONTRASENA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("token", "rol", "aviso");
        assertThat(cuerpo).doesNotContain(CORREO).doesNotContain(usuario.getHashContrasena())
                .doesNotContain("\"id\"").doesNotContain("usuarioId").doesNotContain("hash");
        assertThat(sesiones()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-03 R2: la sesión se guarda con el usuario, creada ahora, expira en 30 min y no revocada")
    void guardaSesionConFechasDelReloj() throws Exception {
        var usuario = crearUsuario(CORREO);
        var ahora = reloj.instant();

        var fila = sesionDe(token(CORREO));

        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(instante(fila.get("creada_en"))).isEqualTo(ahora);
        assertThat(instante(fila.get("expira_en"))).isEqualTo(ahora.plus(Duration.ofMinutes(30)));
        assertThat(fila.get("revocada")).isEqualTo(false);
    }

    @Test
    @DisplayName("HU-03 R3: hash_token es el SHA-256 hex del token devuelto y el token no se guarda en claro")
    void guardaSoloElHashDelToken() throws Exception {
        crearUsuario(CORREO);

        var token = token(CORREO);

        var fila = jdbc.queryForMap("SELECT * FROM sesion");
        assertThat(fila.get("hash_token")).isEqualTo(TokenAleatorio.hash(token));
        assertThat(((String) fila.get("hash_token"))).hasSize(64).isNotEqualTo(token);
        assertThat(fila.values()).noneMatch(valor -> String.valueOf(valor).contains(token));
    }

    @Test
    @DisplayName("HU-03 R4: un arrendador recién registrado (NO_INICIADA) recibe el aviso de identidad")
    void arrendadorRecibeAviso() throws Exception {
        crearArrendador(CORREO);

        iniciar(CORREO, CONTRASENA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ARRENDADOR"))
                .andExpect(jsonPath("$.aviso").value(AVISO));
    }

    @Test
    @DisplayName("HU-03 R5: un USUARIO y un arrendador con identidad VERIFICADA reciben aviso nulo")
    void sinAvisoParaUsuarioYArrendadorVerificado() throws Exception {
        crearUsuario(CORREO);
        var arrendador = crearArrendador(OTRO_CORREO);
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = 'VERIFICADA' WHERE usuario_id = ?",
                arrendador.getId());

        iniciar(CORREO, CONTRASENA).andExpect(status().isCreated()).andExpect(jsonPath("$.aviso").isEmpty());
        iniciar(OTRO_CORREO, CONTRASENA).andExpect(status().isCreated()).andExpect(jsonPath("$.aviso").isEmpty());
    }

    @Test
    @DisplayName("HU-03 R6: dos inicios de sesión de la misma cuenta reciben tokens distintos de 43 caracteres")
    void tokensDistintos() throws Exception {
        crearUsuario(CORREO);

        var primero = token(CORREO);
        var segundo = token(CORREO);

        assertThat(primero).hasSize(43).isNotEqualTo(segundo);
        assertThat(segundo).hasSize(43);
        assertThat(sesiones()).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-03 R8: el rol de la respuesta es el guardado aunque el cuerpo envíe otro")
    void ignoraRolDelCliente() throws Exception {
        crearUsuario(CORREO);

        iniciar("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\",\"rol\":\"ADMIN\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("USUARIO"));
    }

    // ── CA2: mensaje genérico ────────────────────────────────────────────

    @Test
    @DisplayName("HU-03 R9, R10, R11, R12, R13: los cinco rechazos responden 401 con el mismo cuerpo y sin sesión")
    void rechazosConCuerpoIdentico() throws Exception {
        crearUsuario(CORREO);
        var noVerificado = crearUsuario("noverificado@correo.pe");
        var suspendido = crearUsuario("suspendido@correo.pe");
        var admin = crearUsuario("admin@correo.pe");
        jdbc.update("UPDATE usuario SET correo_verificado = false WHERE id = ?", noVerificado.getId());
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", suspendido.getId());
        jdbc.update("UPDATE usuario SET rol = 'ADMIN' WHERE id = ?", admin.getId());

        var cuerpos = new ArrayList<String>();
        for (var intento : List.of(credenciales(CORREO, ERRADA), credenciales("nadie@correo.pe", CONTRASENA),
                credenciales("noverificado@correo.pe", CONTRASENA), credenciales("suspendido@correo.pe", CONTRASENA),
                credenciales("admin@correo.pe", CONTRASENA))) {
            cuerpos.add(iniciar(intento)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value(CREDENCIALES_INVALIDAS))
                    .andReturn().getResponse().getContentAsString());
        }

        assertThat(cuerpos).containsOnly(cuerpos.getFirst());
        assertThat(sesiones()).isZero();
        assertThat(auditorias("LOGIN_FALLIDO")).extracting(fila -> fila.get("detalle")).containsExactly(
                "motivo=CONTRASENA_INCORRECTA", "motivo=CORREO_DESCONOCIDO", "motivo=CORREO_NO_VERIFICADO",
                "motivo=CUENTA_NO_ACTIVA", "motivo=ROL_NO_PERMITIDO");
    }

    @Test
    @DisplayName("HU-03 R15, R18: solicitudes mal formadas → 400 por campo y no cuentan como fallo")
    void malFormadasNoCuentan() throws Exception {
        crearUsuario(CORREO);

        iniciar(credenciales("", CONTRASENA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa tu correo."));
        iniciar(credenciales(CORREO, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa tu contraseña."));
        for (int i = 0; i < 3; i++) {
            iniciar(credenciales(CORREO, "a".repeat(73)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));
        }
        assertThat(sesiones()).isZero();

        fallos(CORREO, 4);
        iniciar(CORREO, CONTRASENA).andExpect(status().isCreated());
    }

    // ── CA3: bloqueo temporal ────────────────────────────────────────────

    @Test
    @DisplayName("HU-03 R16, R32: tras 5 fallos el 6.º intento con la contraseña correcta → 429 sin sesión")
    void bloqueaTrasCincoFallos() throws Exception {
        var usuario = crearUsuario(CORREO);
        fallos(CORREO, 5);

        iniciar(CORREO, CONTRASENA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(sesiones()).isZero();
        var bloqueos = auditorias("LOGIN_BLOQUEADO");
        assertThat(bloqueos).hasSize(1);
        assertThat(((Number) bloqueos.getFirst().get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(bloqueos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(bloqueos.getFirst().get("fecha")).isNotNull();
    }

    @Test
    @DisplayName("HU-03 R17: los fallos con un correo inexistente también bloquean")
    void correoInexistenteTambienBloquea() throws Exception {
        fallos("nadie@correo.pe", 5);

        iniciar("nadie@correo.pe", ERRADA).andExpect(status().isTooManyRequests());

        assertThat(auditorias("LOGIN_BLOQUEADO").getFirst().get("usuario_id")).isNull();
    }

    @Test
    @DisplayName("HU-03 R19: 15 minutos después del último fallo el login correcto vuelve a funcionar")
    void desbloqueaAlPasarLaVentana() throws Exception {
        crearUsuario(CORREO);
        fallos(CORREO, 5);
        reloj.avanzar(Duration.ofMinutes(15).minusSeconds(1));
        iniciar(CORREO, CONTRASENA).andExpect(status().isTooManyRequests());

        reloj.avanzar(Duration.ofSeconds(2));

        iniciar(CORREO, CONTRASENA).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-03 R20: un login correcto reinicia el contador de fallos")
    void loginCorrectoReiniciaContador() throws Exception {
        crearUsuario(CORREO);
        fallos(CORREO, 4);
        iniciar(CORREO, CONTRASENA).andExpect(status().isCreated());

        fallos(CORREO, 4);

        iniciar(CORREO, CONTRASENA).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-03 R21: con un correo bloqueado, otro correo desde la misma IP inicia sesión")
    void otroCorreoMismaIpNoSeBloquea() throws Exception {
        crearUsuario(CORREO);
        crearUsuario(OTRO_CORREO);
        fallos(CORREO, 5);
        iniciar(CORREO, CONTRASENA).andExpect(status().isTooManyRequests());

        iniciar(OTRO_CORREO, CONTRASENA).andExpect(status().isCreated());
    }

    // ── CA4: uso de la sesión, cierre e inactividad ──────────────────────

    @Test
    @DisplayName("HU-03 R22, R23: GET actual responde con el rol de la sesión aunque se envíe usuarioId o rol")
    void actualUsaLaIdentidadDeLaSesion() throws Exception {
        crearUsuario(CORREO);
        var otro = crearArrendador(OTRO_CORREO);
        var token = token(CORREO);

        var cuerpo = mvc.perform(desde(get("/api/sesiones/actual"))
                        .param("usuarioId", String.valueOf(otro.getId()))
                        .param("rol", "ARRENDADOR")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.aviso").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(mapa(cuerpo).keySet()).containsExactlyInAnyOrder("rol", "aviso");
    }

    @Test
    @DisplayName("HU-03 R23: GET actual de un arrendador NO_INICIADA devuelve el aviso de identidad")
    void actualArrendadorConAviso() throws Exception {
        crearArrendador(CORREO);

        actual(token(CORREO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ARRENDADOR"))
                .andExpect(jsonPath("$.aviso").value(AVISO));
    }

    @Test
    @DisplayName("HU-03 R24: cada solicitud autenticada renueva los 30 minutos de inactividad")
    void actividadRenuevaLaSesion() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);

        reloj.avanzar(Duration.ofMinutes(20));
        actual(token).andExpect(status().isOk());
        assertThat(instante(sesionDe(token).get("expira_en"))).isEqualTo(dentroDeMediaHora());
        reloj.avanzar(Duration.ofMinutes(20));

        actual(token).andExpect(status().isOk());
        assertThat(instante(sesionDe(token).get("expira_en"))).isEqualTo(dentroDeMediaHora());
    }

    @Test
    @DisplayName("HU-03 R25, R26, R33: cerrar sesión responde 204, la revoca, la audita y el token deja de valer")
    void cerrarSesionRevoca() throws Exception {
        var usuario = crearUsuario(CORREO);
        var token = token(CORREO);

        cerrar(token).andExpect(status().isNoContent()).andExpect(content().string(""));

        var fila = sesionDe(token);
        assertThat(fila.get("revocada")).isEqualTo(true);
        esperaSesionInvalida(actual(token));
        esperaSesionInvalida(cerrar(token));
        var cierres = auditorias("CIERRE_SESION");
        assertThat(cierres).hasSize(1);
        assertThat(((Number) cierres.getFirst().get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(cierres.getFirst().get("entidad")).isEqualTo("sesion");
        assertThat(((Number) cierres.getFirst().get("entidad_id")).longValue())
                .isEqualTo(((Number) fila.get("id")).longValue());
        assertThat(cierres.getFirst().get("ip")).isEqualTo(ip);
        assertThat(cierres.getFirst().get("fecha")).isNotNull();
    }

    @Test
    @DisplayName("HU-03 R27: tras 30 minutos sin actividad la sesión responde 401 y expira_en no cambia")
    void inactividadInvalidaLaSesion() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);
        var expiraEn = instante(sesionDe(token).get("expira_en"));

        reloj.avanzar(Duration.ofMinutes(30));

        esperaSesionInvalida(actual(token));
        assertThat(instante(sesionDe(token).get("expira_en"))).isEqualTo(expiraEn);
    }

    @Test
    @DisplayName("HU-03 R28: sin encabezado, con esquema Basic o con un token inventado → 401 de sesión inválida")
    void sinTokenValidoResponde401() throws Exception {
        esperaSesionInvalida(mvc.perform(desde(get("/api/sesiones/actual"))));
        esperaSesionInvalida(mvc.perform(desde(get("/api/sesiones/actual"))
                .header(HttpHeaders.AUTHORIZATION, "Basic YW5hOlNlZ3VyYTEyMyE=")));
        esperaSesionInvalida(actual(TokenAleatorio.generar()));
        esperaSesionInvalida(mvc.perform(desde(delete("/api/sesiones/actual"))));
    }

    @Test
    @DisplayName("HU-03 R29: cerrar una sesión no revoca las demás sesiones del mismo usuario")
    void cerrarNoAfectaOtrasSesiones() throws Exception {
        crearUsuario(CORREO);
        var primera = token(CORREO);
        var segunda = token(CORREO);

        cerrar(primera).andExpect(status().isNoContent());

        actual(segunda).andExpect(status().isOk());
        assertThat(sesionDe(segunda).get("revocada")).isEqualTo(false);
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-03 R30: el login correcto audita LOGIN_EXITOSO con el usuario, la sesión, la IP y la fecha")
    void auditaLoginExitoso() throws Exception {
        var usuario = crearUsuario(CORREO);

        var token = token(CORREO);

        var exitos = auditorias("LOGIN_EXITOSO");
        assertThat(exitos).hasSize(1);
        var fila = exitos.getFirst();
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(fila.get("entidad")).isEqualTo("sesion");
        assertThat(((Number) fila.get("entidad_id")).longValue())
                .isEqualTo(((Number) sesionDe(token).get("id")).longValue());
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-03 R31: el fallo audita LOGIN_FALLIDO con el usuario (o nulo), la IP, la fecha y el motivo")
    void auditaLoginFallido() throws Exception {
        var usuario = crearUsuario(CORREO);

        iniciar(CORREO, ERRADA).andExpect(status().isUnauthorized());
        iniciar("nadie@correo.pe", ERRADA).andExpect(status().isUnauthorized());

        var fallidos = auditorias("LOGIN_FALLIDO");
        assertThat(fallidos).hasSize(2);
        assertThat(((Number) fallidos.get(0).get("usuario_id")).longValue()).isEqualTo(usuario.getId());
        assertThat(fallidos.get(0).get("detalle")).isEqualTo("motivo=CONTRASENA_INCORRECTA");
        assertThat(fallidos.get(1).get("usuario_id")).isNull();
        assertThat(fallidos.get(1).get("detalle")).isEqualTo("motivo=CORREO_DESCONOCIDO");
        assertThat(fallidos).allSatisfy(fila -> {
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        });
    }

    @Test
    @DisplayName("HU-03 R34: las auditorías de inicio y cierre no contienen el correo, la contraseña ni el token")
    void auditoriaSinDatosSensibles() throws Exception {
        crearUsuario(CORREO);
        iniciar(CORREO, ERRADA).andExpect(status().isUnauthorized());
        var token = token(CORREO);
        cerrar(token).andExpect(status().isNoContent());
        fallos(CORREO, 5);
        iniciar(CORREO, CONTRASENA).andExpect(status().isTooManyRequests());

        var filas = jdbc.queryForList("SELECT accion, detalle FROM auditoria");
        assertThat(filas).extracting(fila -> fila.get("accion")).contains(
                "LOGIN_FALLIDO", "LOGIN_EXITOSO", "CIERRE_SESION", "LOGIN_BLOQUEADO");
        assertThat(filas).allSatisfy(fila -> assertThat(String.valueOf(fila.get("detalle")))
                .doesNotContain(CORREO).doesNotContain(CONTRASENA).doesNotContain(ERRADA).doesNotContain(token));
    }

    // ── CORS ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-03 R36: el preflight desde el frontend permite el encabezado Authorization")
    void preflightPermiteAuthorization() throws Exception {
        mvc.perform(options("/api/sesiones/actual")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("authorization")));
    }
}
