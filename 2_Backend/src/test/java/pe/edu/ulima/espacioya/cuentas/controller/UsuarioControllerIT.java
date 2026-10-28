package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;

// El origen permitido se fija aquí para que los tests de CORS no dependan de FRONTEND_URL en el entorno.
// Se usa un valor distinto del default de application.properties para probar que CORS lee la propiedad.
@SpringBootTest(properties = "espacioya.frontend.url=" + UsuarioControllerIT.FRONTEND)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, UsuarioControllerIT.Dobles.class})
class UsuarioControllerIT {

    private static final String MENSAJE_RECIBIDO =
            "Te enviamos un correo de verificación. Revisa tu bandeja de entrada.";
    private static final String REQUISITOS = "La contraseña debe tener entre 8 y 64 caracteres, "
            + "al menos una letra y un número, y no puede ser igual a tu correo.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String MENSAJE_LIMITE = "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    private static final String MENSAJE_INESPERADO = "Ocurrió un problema. Intenta de nuevo más tarde.";
    private static final String CONTRASENA = "Segura123!";
    static final String FRONTEND = "http://frontend.espacioya.test:5173";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NotificadorFalso notificador;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private PasswordEncoder encoder;

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
            return new RelojControlable(Instant.parse("2026-10-27T15:00:00Z"));
        }
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<String> correos = new CopyOnWriteArrayList<>();
        private final AtomicBoolean fallar = new AtomicBoolean();

        @Override
        public void notificarVerificacion(String correoNormalizado) {
            if (fallar.get()) {
                throw new IllegalStateException("fallo simulado del servidor de correo");
            }
            correos.add(correoNormalizado);
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
            throw new UnsupportedOperationException("HU-04 no se prueba en esta clase");
        }

        void reiniciar() {
            correos.clear();
            fallar.set(false);
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM usuario");
        notificador.reiniciar();
        ip = siguienteIp();
    }

    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.1." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String cuerpo(String correo, String contrasena, Boolean acepta, String version) {
        return """
                {"correo":%s,"contrasena":%s,"aceptaPrivacidad":%s,"versionPolitica":%s}
                """.formatted(json(correo), json(contrasena), acepta, json(version));
    }

    private static String json(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private static String valido(String correo) {
        return cuerpo(correo, CONTRASENA, true, "2026-10");
    }

    private ResultActions registrar(String json) throws Exception {
        return registrarDesde(ip, json);
    }

    private ResultActions registrarDesde(String origen, String json) throws Exception {
        return mvc.perform(post("/api/usuarios")
                .with(solicitud -> {
                    solicitud.setRemoteAddr(origen);
                    return solicitud;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int usuarios() {
        return jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class);
    }

    private Map<String, Object> usuario(String correo) {
        return jdbc.queryForMap("SELECT * FROM usuario WHERE correo = ?", correo);
    }

    private List<Map<String, Object>> auditoria(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    // ── Registro exitoso (CA1) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-01 R1: crea un USUARIO ACTIVA con el correo verificado")
    void creaUsuarioActivoVerificado() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("rol")).isEqualTo("USUARIO");
        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.get("correo_verificado")).isEqualTo(true);
    }

    @Test
    @DisplayName("HU-01 R2: responde 202 solo con el mensaje de registro recibido")
    void responde202SoloConMensaje() throws Exception {
        registrar(valido("ana@correo.pe"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"mensaje\":\"" + MENSAJE_RECIBIDO + "\"}", true))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.correo").doesNotExist())
                .andExpect(content().string(not(containsString("ana@correo.pe"))));
    }

    @Test
    @DisplayName("HU-01 R3: invoca una vez al notificador con el correo normalizado")
    void invocaAlNotificador() throws Exception {
        registrar(valido("Ana@Correo.PE")).andExpect(status().isAccepted());

        assertThat(notificador.correos).containsExactly("ana@correo.pe");
    }

    @Test
    @DisplayName("HU-01 R5: guarda el correo en minúsculas")
    void guardaCorreoNormalizado() throws Exception {
        registrar(valido("Ana.Perez@Correo.PE")).andExpect(status().isAccepted());

        assertThat(jdbc.queryForList("SELECT correo FROM usuario", String.class))
                .containsExactly("ana.perez@correo.pe");
    }

    // ── Consentimiento (CA2) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-01 R6: rechaza con 400 el registro con aceptaPrivacidad falso")
    void rechazaSinAceptarPrivacidad() throws Exception {
        registrar(cuerpo("ana@correo.pe", CONTRASENA, false, "2026-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores[0].campo").value("aceptaPrivacidad"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("Debes aceptar los términos y la política de privacidad."));

        assertThat(usuarios()).isZero();
        assertThat(notificador.correos).isEmpty();
    }

    @Test
    @DisplayName("HU-01 R6: rechaza con 400 el registro sin aceptaPrivacidad")
    void rechazaSinCampoDeAceptacion() throws Exception {
        registrar("{\"correo\":\"ana@correo.pe\",\"contrasena\":\"Segura123!\",\"versionPolitica\":\"2026-10\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("aceptaPrivacidad"));

        assertThat(usuarios()).isZero();
        assertThat(notificador.correos).isEmpty();
    }

    @Test
    @DisplayName("HU-01 R7: rechaza con 400 una versión de la política distinta de la vigente")
    void rechazaVersionDistinta() throws Exception {
        registrar(cuerpo("ana@correo.pe", CONTRASENA, true, "2025-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("versionPolitica"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("La política de privacidad se actualizó. Recarga la página y vuelve a aceptarla."));

        assertThat(usuarios()).isZero();
        assertThat(notificador.correos).isEmpty();
    }

    @Test
    @DisplayName("HU-01 R7: rechaza con 400 el registro sin versión de la política")
    void rechazaSinVersion() throws Exception {
        registrar(cuerpo("ana@correo.pe", CONTRASENA, true, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("versionPolitica"));

        assertThat(usuarios()).isZero();
        assertThat(notificador.correos).isEmpty();
    }

    @Test
    @DisplayName("HU-01 R8, R9: guarda el consentimiento y audita REGISTRO_USUARIO con IP y versión")
    void guardaConsentimientoYAuditoria() throws Exception {
        var ahora = reloj.instant();

        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("version_politica")).isEqualTo("2026-10");
        assertThat(((Timestamp) fila.get("fecha_consentimiento")).toInstant()).isEqualTo(ahora);
        var eventos = auditoria("REGISTRO_USUARIO");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.getFirst().get("usuario_id")).isEqualTo(fila.get("id"));
        assertThat(eventos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(((Timestamp) eventos.getFirst().get("fecha")).toInstant()).isEqualTo(ahora);
        assertThat((String) eventos.getFirst().get("detalle")).contains("2026-10");
    }

    // ── Contraseña y formato (CA3) ───────────────────────────────────────

    private void esperaRequisitosDeContrasena(String contrasena, String correo) throws Exception {
        registrarDesde(siguienteIp(), cuerpo(correo, contrasena, true, "2026-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(REQUISITOS));
        assertThat(usuarios()).isZero();
    }

    @Test
    @DisplayName("HU-01 R10: rechaza con los requisitos una contraseña corta, larga o de más de 72 bytes")
    void rechazaLongitudInvalida() throws Exception {
        esperaRequisitosDeContrasena("abc1234", "ana@correo.pe");
        esperaRequisitosDeContrasena("a1" + "x".repeat(63), "ana@correo.pe");
        esperaRequisitosDeContrasena("1" + "ñ".repeat(36), "ana@correo.pe");
    }

    @Test
    @DisplayName("HU-01 R11: rechaza con los requisitos una contraseña sin letra o sin dígito")
    void rechazaSinLetraOSinDigito() throws Exception {
        esperaRequisitosDeContrasena("12345678", "ana@correo.pe");
        esperaRequisitosDeContrasena("abcdefgh", "ana@correo.pe");
    }

    @Test
    @DisplayName("HU-01 R12: rechaza con los requisitos una contraseña igual al correo")
    void rechazaIgualAlCorreo() throws Exception {
        esperaRequisitosDeContrasena("ANA1@correo.pe", "ana1@correo.pe");
    }

    @Test
    @DisplayName("HU-01 R13: rechaza con 400 en el campo correo un correo ausente, mal formado o de más de 254")
    void rechazaCorreoInvalido() throws Exception {
        var largo = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".pe";
        assertThat(largo.length()).isGreaterThan(254);
        for (var correo : new String[] {null, "", "no-es-un-correo", largo}) {
            registrarDesde(siguienteIp(), cuerpo(correo, CONTRASENA, true, "2026-10"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa un correo válido."));
        }

        assertThat(usuarios()).isZero();
        assertThat(notificador.correos).isEmpty();
    }

    // ── Correo existente (CA3, STRIDE I) ─────────────────────────────────

    @Test
    @DisplayName("HU-01 R14: con correo existente responde el mismo código y cuerpo que un registro nuevo")
    void correoExistenteMismaRespuesta() throws Exception {
        MvcResult nuevo = registrar(valido("ana@correo.pe")).andReturn();

        MvcResult repetido = registrarDesde(siguienteIp(), valido("ANA@correo.pe")).andReturn();

        assertThat(repetido.getResponse().getStatus()).isEqualTo(nuevo.getResponse().getStatus()).isEqualTo(202);
        assertThat(repetido.getResponse().getContentAsString())
                .isEqualTo(nuevo.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("HU-01 R15: con correo existente no crea usuario, no modifica la cuenta ni notifica")
    void correoExistenteNoCambiaNada() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());
        var antes = usuario("ana@correo.pe");
        notificador.reiniciar();
        reloj.avanzar(Duration.ofMinutes(5));

        registrarDesde(siguienteIp(), cuerpo("ana@correo.pe", "OtraClave456", true, "2026-10"))
                .andExpect(status().isAccepted());

        assertThat(usuarios()).isEqualTo(1);
        assertThat(usuario("ana@correo.pe")).isEqualTo(antes);
        assertThat(notificador.correos).isEmpty();
    }

    @Test
    @DisplayName("HU-01 R16: con correo existente audita REGISTRO_CORREO_EXISTENTE con el id, la IP y la fecha")
    void correoExistenteAuditado() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());
        var titular = usuario("ana@correo.pe").get("id");
        var otraIp = siguienteIp();

        registrarDesde(otraIp, valido("ana@correo.pe")).andExpect(status().isAccepted());

        var eventos = auditoria("REGISTRO_CORREO_EXISTENTE");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.getFirst().get("usuario_id")).isEqualTo(titular);
        assertThat(eventos.getFirst().get("ip")).isEqualTo(otraIp);
        assertThat(((Timestamp) eventos.getFirst().get("fecha")).toInstant()).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-01 R17: un registro inválido con correo existente responde igual que con un correo nuevo")
    void invalidoConCorreoExistenteIgualQueNuevo() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        MvcResult existente = registrarDesde(siguienteIp(), cuerpo("ana@correo.pe", "corta1", true, "2026-10"))
                .andReturn();
        MvcResult nuevo = registrarDesde(siguienteIp(), cuerpo("nuevo@correo.pe", "corta1", true, "2026-10"))
                .andReturn();

        assertThat(existente.getResponse().getStatus()).isEqualTo(nuevo.getResponse().getStatus()).isEqualTo(400);
        assertThat(existente.getResponse().getContentAsString())
                .isEqualTo(nuevo.getResponse().getContentAsString());
        assertThat(auditoria("REGISTRO_CORREO_EXISTENTE")).isEmpty();
    }

    @Test
    @DisplayName("HU-01 R18: dos registros simultáneos con el mismo correo crean una sola cuenta y ambos reciben 202")
    void registrosSimultaneos() throws Exception {
        var listos = new CountDownLatch(1);
        Callable<Integer> registro = () -> {
            listos.await();
            return registrarDesde(siguienteIp(), valido("ana@correo.pe")).andReturn().getResponse().getStatus();
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(registro);
            var b = hilos.submit(registro);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactly(202, 202);
        }

        assertThat(usuarios()).isEqualTo(1);
        assertThat(notificador.correos).containsExactly("ana@correo.pe");
    }

    // ── Hash y rol (STRIDE T y E) ────────────────────────────────────────

    @Test
    @DisplayName("HU-01 R19: guarda la contraseña como hash bcrypt de costo 12 con sal, nunca en claro")
    void guardaHashBcrypt() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());
        registrarDesde(siguienteIp(), valido("beto@correo.pe")).andExpect(status().isAccepted());

        var hashAna = (String) usuario("ana@correo.pe").get("hash_contrasena");
        var hashBeto = (String) usuario("beto@correo.pe").get("hash_contrasena");
        assertThat(hashAna).startsWith("$2a$12$").doesNotContain(CONTRASENA);
        assertThat(encoder.matches(CONTRASENA, hashAna)).isTrue();
        assertThat(hashBeto).isNotEqualTo(hashAna);
    }

    @Test
    @DisplayName("HU-01 R20: ignora rol, estado y correoVerificado enviados por el cliente")
    void ignoraCamposDeRolYEstado() throws Exception {
        registrar("""
                {"correo":"ana@correo.pe","contrasena":"Segura123!","aceptaPrivacidad":true,
                 "versionPolitica":"2026-10","rol":"ADMIN","estado":"SUSPENDIDA","correoVerificado":false}
                """).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("rol")).isEqualTo("USUARIO");
        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.get("correo_verificado")).isEqualTo(true);
    }

    // ── CORS (T7, design §4) ─────────────────────────────────────────────

    private ResultActions preflightDesde(String origenNavegador) throws Exception {
        return mvc.perform(options("/api/usuarios")
                .header(HttpHeaders.ORIGIN, origenNavegador)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"));
    }

    @Test
    @DisplayName("HU-01 R27: el preflight desde espacioya.frontend.url recibe Access-Control-Allow-Origin")
    void preflightDesdeElFrontendPermitido() throws Exception {
        preflightDesde(FRONTEND)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")));
    }

    @Test
    @DisplayName("HU-01 R28: el preflight desde un origen no permitido recibe 403 sin Access-Control-Allow-Origin")
    void preflightDesdeOrigenNoPermitido() throws Exception {
        preflightDesde("http://evil.com")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("HU-01 R27: el POST desde el frontend permitido lleva Access-Control-Allow-Origin")
    void postDesdeElFrontendLlevaCabeceraCors() throws Exception {
        mvc.perform(post("/api/usuarios")
                        .with(solicitud -> {
                            solicitud.setRemoteAddr(ip);
                            return solicitud;
                        })
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valido("ana@correo.pe")))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
    }

    // ── Límite por IP (STRIDE D) ─────────────────────────────────────────

    @Test
    @DisplayName("HU-01 R21: el 6.º intento de una IP en 60 minutos recibe 429 con el mensaje genérico")
    void sextoIntentoRecibe429() throws Exception {
        for (int i = 1; i <= 5; i++) {
            registrar(valido("persona" + i + "@correo.pe")).andExpect(status().isAccepted());
        }

        registrar(valido("persona6@correo.pe"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_LIMITE))
                .andExpect(jsonPath("$.errores").isEmpty());

        assertThat(usuarios()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo = 'persona6@correo.pe'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-01 R22: cuentan también los intentos rechazados con 400 y los de correo existente")
    void cuentanLosIntentosInvalidos() throws Exception {
        registrarDesde(siguienteIp(), valido("ana@correo.pe")).andExpect(status().isAccepted());

        registrar("{ esto no es json").andExpect(status().isBadRequest());
        registrar(cuerpo("no-es-correo", CONTRASENA, true, "2026-10")).andExpect(status().isBadRequest());
        registrar(cuerpo("beto@correo.pe", CONTRASENA, false, "2026-10")).andExpect(status().isBadRequest());
        registrar(cuerpo("beto@correo.pe", "corta1", true, "2026-10")).andExpect(status().isBadRequest());
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        registrar(valido("beto@correo.pe")).andExpect(status().isTooManyRequests());

        assertThat(usuarios()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-01 R23: a los 60 minutos del intento más antiguo la IP bloqueada puede registrarse")
    void aceptaTrasLaVentana() throws Exception {
        for (int i = 1; i <= 5; i++) {
            registrar(valido("persona" + i + "@correo.pe")).andExpect(status().isAccepted());
        }
        registrar(valido("persona6@correo.pe")).andExpect(status().isTooManyRequests());

        reloj.avanzar(Duration.ofMinutes(60));

        registrar(valido("persona6@correo.pe")).andExpect(status().isAccepted());
        assertThat(usuarios()).isEqualTo(6);
    }

    // ── Errores genéricos (STRIDE I) ─────────────────────────────────────

    @Test
    @DisplayName("HU-01 R24, R25: si el notificador falla responde 500 genérico y no deja guardado el usuario")
    void falloDelNotificador() throws Exception {
        notificador.fallar.set(true);

        registrar(valido("ana@correo.pe"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensaje\":\"" + MENSAJE_INESPERADO + "\",\"errores\":[]}", true))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("servidor de correo"))));

        assertThat(usuarios()).isZero();
        assertThat(auditoria("REGISTRO_USUARIO")).isEmpty();
    }
}
