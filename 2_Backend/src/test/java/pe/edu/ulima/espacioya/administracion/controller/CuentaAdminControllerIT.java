package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
import org.springframework.dao.DataIntegrityViolationException;
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
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CuentaAdminControllerIT.Dobles.class})
class CuentaAdminControllerIT {

    private static final String CUENTAS = "/api/admin/cuentas";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String M_BUSQUEDA = "Escribe de 3 a 100 caracteres del correo o del nombre.";
    private static final String M_MOTIVO = "Escribe el motivo de la suspensión (hasta 500 caracteres).";
    private static final String NO_GESTIONABLE = "Esta cuenta no se puede suspender ni reactivar.";
    private static final String YA_SUSPENDIDA = "Esta cuenta ya está suspendida. Actualiza la página.";
    private static final String YA_ACTIVA = "Esta cuenta ya está activa. Actualiza la página.";

    private static final String IP_ADMIN = "127.0.0.1";
    private static final String CORREO_ADMIN = "admin@espacioya.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String MOTIVO = "Publicó el teléfono 912345678 de otra persona.";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_HUELLA = new AtomicInteger(1);
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
    @Autowired
    private NotificadorFalso notificador;
    @Autowired
    private CifradorCampos cifrador;

    // IP no permitida para el panel admin: HU-01…HU-06 usan 10.1.x.x a 10.6.x.x.
    private String ipAjena;
    private Usuario admin;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-29T15:00:00Z"));
        }

        @Bean
        @Primary
        NotificadorFalso notificadorFalso() {
            return new NotificadorFalso();
        }
    }

    record Aviso(String tipo, String correo, String motivo) {
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<Aviso> avisos = new CopyOnWriteArrayList<>();

        @Override
        public void notificarVerificacion(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-09 no registra cuentas por la API");
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
            throw new UnsupportedOperationException("HU-09 no recupera contraseñas");
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-09 no revisa identidades");
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
            throw new UnsupportedOperationException("HU-09 no revisa identidades");
        }

        @Override
        public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
            avisos.add(new Aviso("SUSPENDIDA", correoNormalizado, motivo));
        }

        @Override
        public void notificarCuentaReactivada(String correoNormalizado) {
            avisos.add(new Aviso("REACTIVADA", correoNormalizado, null));
        }

        @Override
        public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
            throw new UnsupportedOperationException("HU-13 no se prueba en esta clase");
        }

        @Override
        public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
            throw new UnsupportedOperationException("HU-13 no se prueba en esta clase");
        }

        @Override
        public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
        }

        @Override
        public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
        }

        List<Aviso> avisos() {
            return avisos;
        }

        void reiniciar() {
            avisos.clear();
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM suspension_cuenta");
        jdbc.update("DELETE FROM revision_identidad");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        // Los contadores de fallos de inicio de sesión viven en memoria: una hora después ya no cuentan.
        reloj.avanzar(Duration.ofHours(1));
        notificador.reiniciar();
        ipAjena = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
        admin = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hashContrasena, "2026-10", reloj.instant()));
        // Sin MFA activo el admin no tiene sesión (HU-17 CA1, R28).
        MfaDePrueba.activar(jdbc, cifrador, admin.getId(), reloj.instant());
    }

    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.9." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    private Usuario crearArrendador(String correo, String nombre) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, nombre, "987654321",
                "2026-10", reloj.instant()));
        var huella = HexFormat.of().toHexDigits(SIGUIENTE_HUELLA.getAndIncrement()).repeat(8);
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970", huella,
                "Razón de " + nombre, "Av. Javier Prado 4600"));
        return usuario;
    }

    private void forzarEstado(Usuario usuario, String estado) {
        jdbc.update("UPDATE usuario SET estado = ? WHERE id = ?", estado, usuario.getId());
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud, String ip) {
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        });
        return solicitud;
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T con(T solicitud, String token, String ip) {
        desde(solicitud, ip);
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return solicitud;
    }

    private ResultActions iniciarSesion(String correo, String contrasena, String ip) throws Exception {
        return mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"));
    }

    private String token(String correo, String ip) throws Exception {
        var cuerpo = cuerpo(iniciarSesion(correo, CONTRASENA, ip).andExpect(status().isCreated()));
        return (String) mapa(cuerpo).get("token");
    }

    private String tokenAdmin() throws Exception {
        return MfaDePrueba.iniciarSesion(mvc, jdbc, CORREO_ADMIN, CONTRASENA, IP_ADMIN, reloj.instant());
    }

    private ResultActions buscar(String token, String ip, String q) throws Exception {
        var solicitud = get(CUENTAS);
        if (q != null) {
            solicitud.queryParam("q", q);
        }
        return mvc.perform(con(solicitud, token, ip));
    }

    private ResultActions detalle(String token, String ip, Long id) throws Exception {
        return mvc.perform(con(get(CUENTAS + "/" + id), token, ip));
    }

    private ResultActions suspender(String token, String ip, Long id, String cuerpo) throws Exception {
        return mvc.perform(con(post(CUENTAS + "/" + id + "/suspension"), token, ip)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions reactivar(String token, String ip, Long id) throws Exception {
        return mvc.perform(con(post(CUENTAS + "/" + id + "/reactivacion"), token, ip));
    }

    private static String motivo(String texto) {
        return "{\"motivo\":\"" + texto + "\"}";
    }

    private List<ResultActions> losCuatroEndpoints(String token, String ip, Long id) throws Exception {
        return List.of(buscar(token, ip, "correo"), detalle(token, ip, id), suspender(token, ip, id, motivo(MOTIVO)),
                reactivar(token, ip, id));
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    private static String cuerpo(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cuentasDe(Map<String, Object> busqueda) {
        return (List<Map<String, Object>>) busqueda.get("cuentas");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> submapa(Map<String, Object> padre, String clave) {
        return (Map<String, Object>) padre.get(clave);
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private String estado(Usuario usuario) {
        return jdbc.queryForObject("SELECT estado FROM usuario WHERE id = ?", String.class, usuario.getId());
    }

    private List<Map<String, Object>> registros() {
        return jdbc.queryForList("SELECT * FROM suspension_cuenta ORDER BY id");
    }

    private List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    private List<Boolean> sesionesRevocadas(Usuario usuario) {
        return jdbc.queryForList("SELECT revocada FROM sesion WHERE usuario_id = ? ORDER BY id", Boolean.class,
                usuario.getId());
    }

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    private static long id(Object valor) {
        return ((Number) valor).longValue();
    }

    private void sinCambios(Usuario cuenta, String estadoEsperado) {
        assertThat(estado(cuenta)).isEqualTo(estadoEsperado);
        assertThat(registros()).isEmpty();
        assertThat(auditorias("CUENTA_SUSPENDIDA")).isEmpty();
        assertThat(auditorias("CUENTA_REACTIVADA")).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    // ── Control de IPs, sesión y rol (R41, R42) ──────────────────────────

    @Test
    @DisplayName("HU-09 R42: desde una IP no permitida, con o sin sesión de admin, los 4 endpoints → 403 sin "
            + "ejecutar nada")
    void ipNoPermitidaResponde403() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        var token = tokenAdmin();

        for (var conToken : new String[] {token, null}) {
            for (var resultado : losCuatroEndpoints(conToken, ipAjena, arrendador.getId())) {
                resultado.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO))
                        .andExpect(jsonPath("$.errores").isEmpty());
            }
        }
        sinCambios(arrendador, "ACTIVA");
    }

    @Test
    @DisplayName("HU-09 R41: una sesión USUARIO o ARRENDADOR desde una IP permitida, con cuerpo válido, recibe 403 en "
            + "los 4 endpoints")
    void rolDistintoDeAdminResponde403() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        crearUsuario("beto@correo.pe");

        for (var correo : new String[] {"beto@correo.pe", "ana@correo.pe"}) {
            var token = token(correo, IP_ADMIN);
            for (var resultado : losCuatroEndpoints(token, IP_ADMIN, arrendador.getId())) {
                resultado.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
            }
        }
        sinCambios(arrendador, "ACTIVA");
    }

    // ── Búsqueda (R1–R6, R13) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-09 R1, R5, R13: la búsqueda devuelve solo id, nombre, correo, rol y estado de cada cuenta que "
            + "coincide, de cualquier rol y estado, por correo, con hayMas y no-store")
    void buscaPorCorreoTodasLasCuentas() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        var eliminada = crearUsuario("eliminada@correo.pe");
        forzarEstado(eliminada, "ELIMINADA");
        var suspendida = crearUsuario("suspendida@correo.pe");
        forzarEstado(suspendida, "SUSPENDIDA");
        crearUsuario("beto@otro.pe");

        var resultado = buscar(tokenAdmin(), IP_ADMIN, "correo.pe")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        var busqueda = mapa(cuerpo(resultado));
        assertThat(busqueda).containsOnlyKeys("cuentas", "hayMas");
        assertThat(busqueda.get("hayMas")).isEqualTo(false);
        var cuentas = cuentasDe(busqueda);
        assertThat(cuentas).extracting(cuenta -> cuenta.get("correo"))
                .containsExactly("ana@correo.pe", "eliminada@correo.pe", "suspendida@correo.pe");
        assertThat(cuentas).allSatisfy(cuenta -> assertThat(cuenta)
                .containsOnlyKeys("id", "nombre", "correo", "rol", "estado"));
        assertThat(cuentas.getFirst()).containsEntry("nombre", "Ana Torres").containsEntry("rol", "ARRENDADOR")
                .containsEntry("estado", "ACTIVA");
        assertThat(id(cuentas.getFirst().get("id"))).isEqualTo(arrendador.getId());
        assertThat(cuentas.get(1)).containsEntry("rol", "USUARIO").containsEntry("estado", "ELIMINADA")
                .containsEntry("nombre", null);
        assertThat(cuentas.get(2)).containsEntry("estado", "SUSPENDIDA");

        var admins = cuentasDe(mapa(cuerpo(buscar(tokenAdmin(), IP_ADMIN, "espacioya").andExpect(status().isOk()))));
        assertThat(admins).extracting(cuenta -> cuenta.get("rol")).containsExactly("ADMIN");
        assertThat(id(admins.getFirst().get("id"))).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("HU-09 R1: la búsqueda no distingue mayúsculas en el texto, el correo ni el nombre, y recorta q")
    void buscaSinDistinguirMayusculas() throws Exception {
        crearArrendador("zeta@correo.pe", "Ana Torres");
        crearUsuario("beto@correo.pe");
        var token = tokenAdmin();

        var porNombre = cuentasDe(mapa(cuerpo(buscar(token, IP_ADMIN, "  ANA TOR  ").andExpect(status().isOk()))));
        var porCorreo = cuentasDe(mapa(cuerpo(buscar(token, IP_ADMIN, "BETO@").andExpect(status().isOk()))));

        assertThat(porNombre).extracting(cuenta -> cuenta.get("correo")).containsExactly("zeta@correo.pe");
        assertThat(porCorreo).extracting(cuenta -> cuenta.get("correo")).containsExactly("beto@correo.pe");
    }

    @Test
    @DisplayName("HU-09 R2, R3: con 21 coincidencias devuelve las 20 primeras por correo y hayMas; con 20, sin hayMas")
    void maximoVeinteConHayMas() throws Exception {
        for (var i = 21; i >= 1; i--) {
            crearUsuario("c%02d@lote.pe".formatted(i));
        }
        var token = tokenAdmin();

        var busqueda = mapa(cuerpo(buscar(token, IP_ADMIN, "@lote.pe").andExpect(status().isOk())));
        assertThat(busqueda.get("hayMas")).isEqualTo(true);
        assertThat(cuentasDe(busqueda)).extracting(cuenta -> cuenta.get("correo"))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 20)
                        .mapToObj("c%02d@lote.pe"::formatted).toList());

        jdbc.update("DELETE FROM usuario WHERE correo = 'c21@lote.pe'");
        var veinte = mapa(cuerpo(buscar(token, IP_ADMIN, "@lote.pe").andExpect(status().isOk())));
        assertThat(veinte.get("hayMas")).isEqualTo(false);
        assertThat(cuentasDe(veinte)).hasSize(20);
    }

    @Test
    @DisplayName("HU-09 R4: %, _ y \\ del texto se buscan como caracteres literales")
    void comodinesLiterales() throws Exception {
        for (var correo : new String[] {"p%q@x.pe", "pzzq@x.pe", "u_v@x.pe", "uxv@x.pe", "r\\s@x.pe", "rs@x.pe"}) {
            crearUsuario(correo);
        }
        var token = tokenAdmin();

        for (var caso : List.of(Map.entry("p%q", "p%q@x.pe"), Map.entry("u_v", "u_v@x.pe"),
                Map.entry("r\\s", "r\\s@x.pe"))) {
            var cuentas = cuentasDe(mapa(cuerpo(buscar(token, IP_ADMIN, caso.getKey()).andExpect(status().isOk()))));
            assertThat(cuentas).extracting(cuenta -> cuenta.get("correo")).containsExactly(caso.getValue());
        }
    }

    @Test
    @DisplayName("HU-09 R6: sin q, con menos de 3 caracteres tras recortar o con 101 → 400 en el campo q")
    void busquedaInvalidaResponde400() throws Exception {
        var token = tokenAdmin();

        for (var q : new String[] {null, "", "  ab ", "a".repeat(101)}) {
            buscar(token, IP_ADMIN, q)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("q"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(M_BUSQUEDA));
        }
        buscar(token, IP_ADMIN, "a".repeat(100)).andExpect(status().isOk());
    }

    // ── Detalle (R7–R13) ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-09 R7, R8, R9, R11, R13: el detalle de un ARRENDADOR ACTIVA trae solo los datos de la cuenta, su "
            + "identidad, puede suspenderse, sin suspensión y con no-store")
    void detalleDeArrendadorActiva() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");

        var resultado = detalle(tokenAdmin(), IP_ADMIN, arrendador.getId())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        var cuerpo = cuerpo(resultado);
        var respuesta = mapa(cuerpo);
        assertThat(respuesta).containsOnlyKeys("cuenta", "suspension");
        assertThat(respuesta.get("suspension")).isNull();
        var cuenta = submapa(respuesta, "cuenta");
        assertThat(cuenta).containsOnlyKeys("id", "nombre", "correo", "telefono", "rol", "estado", "correoVerificado",
                "estadoIdentidad", "puedeSuspender", "puedeReactivar");
        assertThat(id(cuenta.get("id"))).isEqualTo(arrendador.getId());
        assertThat(cuenta).containsEntry("nombre", "Ana Torres").containsEntry("correo", "ana@correo.pe")
                .containsEntry("telefono", "987654321").containsEntry("rol", "ARRENDADOR")
                .containsEntry("estado", "ACTIVA").containsEntry("correoVerificado", true)
                .containsEntry("estadoIdentidad", "NO_INICIADA").containsEntry("puedeSuspender", true)
                .containsEntry("puedeReactivar", false);
        assertThat(cuerpo).doesNotContain("20100070970").doesNotContain("Razón de").doesNotContain("Javier Prado");
    }

    @Test
    @DisplayName("HU-09 R8, R9: un USUARIO no tiene identidad; ADMIN, el propio admin y ELIMINADA no tienen acciones")
    void detalleBanderasPorRolYEstado() throws Exception {
        var usuario = crearUsuario("beto@correo.pe");
        var otroAdmin = usuarios.save(Usuario.registrarAdmin("admin2@espacioya.pe", hashContrasena, "2026-10",
                reloj.instant()));
        var eliminada = crearUsuario("eliminada@correo.pe");
        forzarEstado(eliminada, "ELIMINADA");
        var token = tokenAdmin();

        var deUsuario = submapa(mapa(cuerpo(detalle(token, IP_ADMIN, usuario.getId()).andExpect(status().isOk()))),
                "cuenta");
        assertThat(deUsuario).containsEntry("estadoIdentidad", null).containsEntry("puedeSuspender", true)
                .containsEntry("puedeReactivar", false);
        for (var id : new Long[] {otroAdmin.getId(), admin.getId(), eliminada.getId()}) {
            var cuenta = submapa(mapa(cuerpo(detalle(token, IP_ADMIN, id).andExpect(status().isOk()))), "cuenta");
            assertThat(cuenta).containsEntry("estadoIdentidad", null).containsEntry("puedeSuspender", false)
                    .containsEntry("puedeReactivar", false);
        }
    }

    @Test
    @DisplayName("HU-09 R9, R10, R11: suspendida trae motivo y fecha de su última suspensión y puede reactivarse; "
            + "tras reactivar, suspensión nula")
    void detalleConSuspensionVigente() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        var token = tokenAdmin();
        suspender(token, IP_ADMIN, arrendador.getId(), motivo("Primer motivo")).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(5));
        reactivar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(5));
        suspender(token, IP_ADMIN, arrendador.getId(), motivo("  " + MOTIVO + " ")).andExpect(status().isCreated());
        var suspendidaEn = reloj.instant();
        reloj.avanzar(Duration.ofMinutes(5));

        var suspendida = mapa(cuerpo(detalle(token, IP_ADMIN, arrendador.getId()).andExpect(status().isOk())));
        assertThat(submapa(suspendida, "suspension")).containsOnlyKeys("motivo", "fecha")
                .containsEntry("motivo", MOTIVO);
        assertThat(Instant.parse((String) submapa(suspendida, "suspension").get("fecha"))).isEqualTo(suspendidaEn);
        assertThat(submapa(suspendida, "cuenta")).containsEntry("estado", "SUSPENDIDA")
                .containsEntry("puedeSuspender", false).containsEntry("puedeReactivar", true);

        reactivar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isCreated());
        var reactivada = mapa(cuerpo(detalle(token, IP_ADMIN, arrendador.getId()).andExpect(status().isOk())));
        assertThat(reactivada.get("suspension")).isNull();
        assertThat(submapa(reactivada, "cuenta")).containsEntry("estado", "ACTIVA");
    }

    @Test
    @DisplayName("HU-09 R12: el detalle de un id inexistente → 404 con el mensaje genérico")
    void detalleInexistenteResponde404() throws Exception {
        detalle(tokenAdmin(), IP_ADMIN, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    // ── Suspensión (R14–R21) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-09 R14, R15, R16, R17, R18: suspender → 201; cuenta SUSPENDIDA, sus sesiones revocadas, registro "
            + "con el admin de la sesión y un aviso con el motivo recortado")
    void suspenderCuenta() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        var otro = crearUsuario("beto@correo.pe");
        var tokenAna1 = token("ana@correo.pe", ipAjena);
        token("ana@correo.pe", ipAjena);
        var tokenBeto = token("beto@correo.pe", ipAjena);
        var token = tokenAdmin();

        var resultado = suspender(token, IP_ADMIN, arrendador.getId(),
                "{\"motivo\":\"  " + MOTIVO + "  \",\"adminId\":" + otro.getId() + ",\"estado\":\"ACTIVA\"}")
                .andExpect(status().isCreated());

        var respuesta = mapa(cuerpo(resultado));
        assertThat(respuesta).containsOnlyKeys("accion", "estado", "fecha");
        assertThat(respuesta).containsEntry("accion", "SUSPENSION").containsEntry("estado", "SUSPENDIDA");
        assertThat(Instant.parse((String) respuesta.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(estado(arrendador)).isEqualTo("SUSPENDIDA");
        assertThat(sesionesRevocadas(arrendador)).containsExactly(true, true);
        assertThat(sesionesRevocadas(otro)).containsExactly(false);
        var fila = registros().getFirst();
        assertThat(registros()).hasSize(1);
        assertThat(id(fila.get("usuario_id"))).isEqualTo(arrendador.getId());
        assertThat(id(fila.get("admin_id"))).isEqualTo(admin.getId());
        assertThat(fila.get("accion")).isEqualTo("SUSPENSION");
        assertThat(fila.get("motivo")).isEqualTo(MOTIVO);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(notificador.avisos()).containsExactly(new Aviso("SUSPENDIDA", "ana@correo.pe", MOTIVO));
        mvc.perform(con(get("/api/sesiones/actual"), tokenAna1, ipAjena))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        mvc.perform(con(get("/api/sesiones/actual"), tokenBeto, ipAjena)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-09 R19, R39: sin motivo, vacío, solo espacios o de 501 caracteres → 400 en el campo motivo sin "
            + "cambiar la cuenta ni sus sesiones")
    void motivoInvalidoResponde400() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        token("ana@correo.pe", ipAjena);
        var token = tokenAdmin();

        for (var cuerpo : new String[] {"{}", "{\"motivo\":null}", motivo(""), motivo("   "),
                motivo("a".repeat(501))}) {
            suspender(token, IP_ADMIN, arrendador.getId(), cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(M_MOTIVO));
        }
        sinCambios(arrendador, "ACTIVA");
        assertThat(sesionesRevocadas(arrendador)).containsExactly(false);
        suspender(token, IP_ADMIN, arrendador.getId(), motivo("a".repeat(500))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-09 R20, R33: suspendida, su inicio de sesión responde igual que una contraseña incorrecta; "
            + "reactivada, vuelve a iniciar sesión")
    void inicioDeSesionTrasSuspenderYReactivar() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        crearUsuario("beto@correo.pe");
        var token = tokenAdmin();
        var incorrecta = cuerpo(iniciarSesion("beto@correo.pe", "Otra123!x", ipAjena)
                .andExpect(status().isUnauthorized()));
        var sesionesAntes = jdbc.queryForObject("SELECT count(*) FROM sesion WHERE usuario_id = ?", Long.class,
                arrendador.getId());

        suspender(token, IP_ADMIN, arrendador.getId(), motivo(MOTIVO)).andExpect(status().isCreated());
        var suspendida = cuerpo(iniciarSesion("ana@correo.pe", CONTRASENA, ipAjena)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(CREDENCIALES_INVALIDAS)));
        assertThat(suspendida).isEqualTo(incorrecta);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion WHERE usuario_id = ?", Long.class,
                arrendador.getId())).isEqualTo(sesionesAntes);

        reactivar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isCreated());
        var nuevo = token("ana@correo.pe", ipAjena);
        mvc.perform(con(get("/api/sesiones/actual"), nuevo, ipAjena)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ARRENDADOR"));
    }

    @Test
    @DisplayName("HU-09 R21: una sesión vigente creada después de suspender (carrera con el login) responde 401 sin "
            + "renovarse")
    void sesionDeCuentaSuspendidaResponde401() throws Exception {
        var usuario = crearUsuario("beto@correo.pe");
        suspender(tokenAdmin(), IP_ADMIN, usuario.getId(), motivo(MOTIVO)).andExpect(status().isCreated());
        var token = TokenAleatorio.generar();
        var creada = reloj.instant();
        var expira = creada.plus(Duration.ofMinutes(30));
        jdbc.update("INSERT INTO sesion (usuario_id, hash_token, creada_en, expira_en, revocada) "
                + "VALUES (?, ?, ?, ?, FALSE)", usuario.getId(), TokenAleatorio.hash(token), Timestamp.from(creada),
                Timestamp.from(expira));
        reloj.avanzar(Duration.ofMinutes(10));

        mvc.perform(con(get("/api/sesiones/actual"), token, ipAjena))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));

        var fila = jdbc.queryForMap("SELECT expira_en, revocada FROM sesion WHERE hash_token = ?",
                TokenAleatorio.hash(token));
        assertThat(instante(fila.get("expira_en"))).isEqualTo(expira);
        assertThat(fila.get("revocada")).isEqualTo(false);
    }

    // ── Espacios (R26) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-09 R26: suspender y reactivar al arrendador no cambia el estado de su espacio PUBLICADO")
    void espacioConservaSuEstado() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        var espacioId = jdbc.queryForObject("""
                INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, precio_hora, descripcion,
                                     estado, creado_en, actualizado_en)
                VALUES (?, 'Cancha Los Olivos', 'CANCHA', 'Av. Universitaria 1234', 'LOS_OLIVOS', 20, 80,
                        'Cancha de grass sintético techada.', 'PUBLICADO', now(), now())
                RETURNING id
                """, Long.class, arrendador.getId());
        var token = tokenAdmin();

        suspender(token, IP_ADMIN, arrendador.getId(), motivo(MOTIVO)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, espacioId))
                .isEqualTo("PUBLICADO");
        reactivar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, espacioId))
                .isEqualTo("PUBLICADO");
    }

    // ── Reactivación (R29–R32) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-09 R29, R30, R31, R32: reactivar → 201; cuenta ACTIVA, registro sin motivo con el admin de la "
            + "sesión y un aviso de reactivación")
    void reactivarCuenta() throws Exception {
        var usuario = crearUsuario("beto@correo.pe");
        var token = tokenAdmin();
        suspender(token, IP_ADMIN, usuario.getId(), motivo(MOTIVO)).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(2));

        var resultado = mvc.perform(con(post(CUENTAS + "/" + usuario.getId() + "/reactivacion"), token, IP_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"ignorado\"}"))
                .andExpect(status().isCreated());

        var respuesta = mapa(cuerpo(resultado));
        assertThat(respuesta).containsOnlyKeys("accion", "estado", "fecha");
        assertThat(respuesta).containsEntry("accion", "REACTIVACION").containsEntry("estado", "ACTIVA");
        assertThat(Instant.parse((String) respuesta.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(estado(usuario)).isEqualTo("ACTIVA");
        var filas = registros();
        assertThat(filas).hasSize(2);
        var fila = filas.get(1);
        assertThat(id(fila.get("usuario_id"))).isEqualTo(usuario.getId());
        assertThat(id(fila.get("admin_id"))).isEqualTo(admin.getId());
        assertThat(fila.get("accion")).isEqualTo("REACTIVACION");
        assertThat(fila.get("motivo")).isNull();
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(notificador.avisos()).containsExactly(new Aviso("SUSPENDIDA", "beto@correo.pe", MOTIVO),
                new Aviso("REACTIVADA", "beto@correo.pe", null));
    }

    // ── Estado, concurrencia y fallos (R34–R39) ──────────────────────────

    @Test
    @DisplayName("HU-09 R34, R39: suspender o reactivar un id inexistente → 404 sin registro ni aviso")
    void cuentaInexistenteResponde404() throws Exception {
        var token = tokenAdmin();

        suspender(token, IP_ADMIN, 999_999L, motivo(MOTIVO)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        reactivar(token, IP_ADMIN, 999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));

        assertThat(registros()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    @Test
    @DisplayName("HU-09 R35, R39: suspender o reactivar otro ADMIN, al propio admin o una cuenta ELIMINADA → 400 no "
            + "gestionable sin cambios")
    void cuentaNoGestionableResponde400() throws Exception {
        var otroAdmin = usuarios.save(Usuario.registrarAdmin("admin2@espacioya.pe", hashContrasena, "2026-10",
                reloj.instant()));
        var eliminada = crearUsuario("eliminada@correo.pe");
        forzarEstado(eliminada, "ELIMINADA");
        var token = tokenAdmin();

        for (var cuenta : List.of(otroAdmin, admin, eliminada)) {
            suspender(token, IP_ADMIN, cuenta.getId(), motivo(MOTIVO)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(NO_GESTIONABLE))
                    .andExpect(jsonPath("$.errores").isEmpty());
            reactivar(token, IP_ADMIN, cuenta.getId()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(NO_GESTIONABLE));
        }
        assertThat(estado(otroAdmin)).isEqualTo("ACTIVA");
        assertThat(estado(admin)).isEqualTo("ACTIVA");
        sinCambios(eliminada, "ELIMINADA");
        mvc.perform(con(get("/api/sesiones/actual"), token, IP_ADMIN)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-09 R36, R37, R39: suspender una cuenta ya SUSPENDIDA o reactivar una ACTIVA → 400 sin registro "
            + "ni aviso")
    void estadoIgualResponde400() throws Exception {
        var activa = crearUsuario("beto@correo.pe");
        var suspendida = crearArrendador("ana@correo.pe", "Ana Torres");
        forzarEstado(suspendida, "SUSPENDIDA");
        var token = tokenAdmin();

        suspender(token, IP_ADMIN, suspendida.getId(), motivo(MOTIVO)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(YA_SUSPENDIDA))
                .andExpect(jsonPath("$.errores").isEmpty());
        reactivar(token, IP_ADMIN, activa.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(YA_ACTIVA));

        assertThat(estado(suspendida)).isEqualTo("SUSPENDIDA");
        sinCambios(activa, "ACTIVA");
    }

    @Test
    @DisplayName("HU-09 R38: dos suspensiones simultáneas de la misma cuenta → una 201 y otra 400 ya suspendida, un "
            + "registro y un aviso")
    void suspensionesSimultaneas() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        var token = tokenAdmin();
        var listos = new CountDownLatch(1);
        var cuerpos = new CopyOnWriteArrayList<String>();
        Callable<Integer> suspenderALaVez = () -> {
            listos.await();
            var respuesta = suspender(token, IP_ADMIN, arrendador.getId(), motivo(MOTIVO)).andReturn().getResponse();
            cuerpos.add(respuesta.getContentAsString());
            return respuesta.getStatus();
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(suspenderALaVez);
            var b = hilos.submit(suspenderALaVez);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 400);
        }

        assertThat(cuerpos).anySatisfy(cuerpo -> assertThat(cuerpo).contains(YA_SUSPENDIDA));
        assertThat(estado(arrendador)).isEqualTo("SUSPENDIDA");
        assertThat(registros()).hasSize(1);
        assertThat(auditorias("CUENTA_SUSPENDIDA")).hasSize(1);
        assertThat(notificador.avisos()).hasSize(1);
    }

    // ── Base de datos y auditoría (R43–R46) ──────────────────────────────

    @Test
    @DisplayName("HU-09 R43: la BD rechaza una acción desconocida, una SUSPENSION sin motivo o en blanco y una "
            + "REACTIVACION con motivo")
    void restriccionesDeLaTabla() {
        var usuario = crearUsuario("beto@correo.pe");
        var insertar = "INSERT INTO suspension_cuenta (usuario_id, admin_id, accion, motivo, fecha) "
                + "VALUES (?, ?, ?, ?, now())";

        for (var invalida : List.of(new String[] {"BLOQUEO", "Motivo"}, new String[] {"SUSPENSION", null},
                new String[] {"SUSPENSION", "   "}, new String[] {"REACTIVACION", "Todo bien"})) {
            assertThatThrownBy(() -> jdbc.update(insertar, usuario.getId(), admin.getId(), invalida[0], invalida[1]))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update(insertar, usuario.getId(), admin.getId(), "SUSPENSION", MOTIVO);
        jdbc.update(insertar, usuario.getId(), admin.getId(), "REACTIVACION", null);
        assertThat(registros()).hasSize(2);
    }

    @Test
    @DisplayName("HU-09 R44, R45, R46: cada acción se audita con el admin, la cuenta, la IP, la fecha y "
            + "accion=…;registro=…, sin motivo ni correo")
    void auditaCadaAccion() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Torres");
        token("ana@correo.pe", ipAjena);
        token("ana@correo.pe", ipAjena);
        var token = tokenAdmin();
        buscar(token, IP_ADMIN, "ana@").andExpect(status().isOk());
        detalle(token, IP_ADMIN, arrendador.getId()).andExpect(status().isOk());

        suspender(token, IP_ADMIN, arrendador.getId(), motivo(MOTIVO)).andExpect(status().isCreated());
        var suspendidaEn = reloj.instant();
        reloj.avanzar(Duration.ofMinutes(1));
        reactivar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isCreated());

        var filas = registros();
        var suspensiones = auditorias("CUENTA_SUSPENDIDA");
        var reactivaciones = auditorias("CUENTA_REACTIVADA");
        assertThat(suspensiones).hasSize(1);
        assertThat(reactivaciones).hasSize(1);
        verificarAuditoria(suspensiones.getFirst(), arrendador, suspendidaEn,
                "accion=SUSPENSION;registro=" + filas.get(0).get("id") + ";sesionesRevocadas=2");
        verificarAuditoria(reactivaciones.getFirst(), arrendador, reloj.instant(),
                "accion=REACTIVACION;registro=" + filas.get(1).get("id"));
        var detalles = new ArrayList<String>();
        jdbc.queryForList("SELECT detalle FROM auditoria", String.class).forEach(d -> detalles.add(String.valueOf(d)));
        assertThat(detalles).allSatisfy(detalle -> assertThat(detalle).doesNotContain(MOTIVO)
                .doesNotContain("912345678").doesNotContain("ana@correo.pe"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE entidad = 'usuario' AND usuario_id = ?",
                Long.class, admin.getId())).isEqualTo(2L);
    }

    private void verificarAuditoria(Map<String, Object> fila, Usuario cuenta, Instant fecha, String detalle) {
        assertThat(id(fila.get("usuario_id"))).isEqualTo(admin.getId());
        assertThat(fila.get("entidad")).isEqualTo("usuario");
        assertThat(id(fila.get("entidad_id"))).isEqualTo(cuenta.getId());
        assertThat(fila.get("ip")).isEqualTo(IP_ADMIN);
        assertThat(instante(fila.get("fecha"))).isEqualTo(fecha);
        assertThat(fila.get("detalle")).isEqualTo(detalle);
    }
}
