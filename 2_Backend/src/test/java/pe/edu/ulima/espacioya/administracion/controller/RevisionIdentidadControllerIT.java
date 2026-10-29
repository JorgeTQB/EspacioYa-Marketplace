package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RevisionIdentidadControllerIT.Dobles.class})
class RevisionIdentidadControllerIT {

    private static final String COLA = "/api/admin/arrendadores/en-revision";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String YA_REVISADA = "Este arrendador ya no está en revisión. Actualiza la cola.";
    private static final String M_MOTIVO = "Escribe el motivo del rechazo (hasta 500 caracteres).";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String AVISO_RECHAZADA = "Tu identidad fue rechazada. Vuelve a subir tus documentos. Motivo: ";

    private static final String IP_ADMIN = "127.0.0.1";
    private static final String CORREO_ADMIN = "admin@espacioya.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String NUMERO_DOCUMENTO = "20100070970";
    private static final String MOTIVO = "La foto del DNI está borrosa.";
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
    @Value("${espacioya.almacenamiento.directorio}")
    private Path directorio;

    // IP no permitida para el panel admin: HU-01…HU-05 usan 10.1.x.x a 10.5.x.x.
    private String ipAjena;
    private Usuario admin;

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

    record Aviso(String tipo, String correo, String motivo) {
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<Aviso> avisos = new CopyOnWriteArrayList<>();

        @Override
        public void notificarVerificacion(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-06 no registra cuentas por la API");
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
            throw new UnsupportedOperationException("HU-06 no recupera contraseñas");
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
            avisos.add(new Aviso("APROBADA", correoNormalizado, null));
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
            avisos.add(new Aviso("RECHAZADA", correoNormalizado, motivo));
        }

        List<Aviso> avisos() {
            return avisos;
        }

        void reiniciar() {
            avisos.clear();
        }
    }

    @BeforeEach
    void preparar() throws IOException {
        jdbc.update("DELETE FROM revision_identidad");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        vaciarDirectorio();
        reloj.avanzar(Duration.ofHours(1));
        notificador.reiniciar();
        ipAjena = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
        admin = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hashContrasena, "2026-10", reloj.instant()));
    }

    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.6." + (n / 250) + "." + (n % 250 + 1);
    }

    private void vaciarDirectorio() throws IOException {
        if (!Files.exists(directorio)) {
            return;
        }
        try (Stream<Path> rutas = Files.walk(directorio)) {
            for (var ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                if (!ruta.equals(directorio)) {
                    Files.delete(ruta);
                }
            }
        }
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    private Usuario crearArrendador(String correo, String nombre) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, nombre, "987654321",
                "2026-10", reloj.instant()));
        var huella = HexFormat.of().toHexDigits(SIGUIENTE_HUELLA.getAndIncrement()).repeat(8);
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, NUMERO_DOCUMENTO, huella,
                "Razón de " + nombre, "Av. Javier Prado 4600"));
        return usuario;
    }

    // El arrendador envía sus documentos por la API de HU-05: queda EN_REVISION con fecha de envío = ahora.
    private String enviarDocumentos(String correo) throws Exception {
        var token = token(correo, ipAjena);
        mvc.perform(desde(multipart("/api/arrendadores/actual/documentos")
                                .file(new MockMultipartFile("documentoIdentidad", "dni.pdf", "application/pdf",
                                        ArchivosDePrueba.pdf(2048)))
                                .file(new MockMultipartFile("documentoPropiedad", "titulo.png", "image/png",
                                        ArchivosDePrueba.png(40, 30)))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token), ipAjena))
                .andExpect(status().isCreated());
        return token;
    }

    private Usuario enRevision(String correo, String nombre) throws Exception {
        var usuario = crearArrendador(correo, nombre);
        enviarDocumentos(correo);
        return usuario;
    }

    private void forzarEstado(Usuario usuario, String estado, String motivo) {
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = ?, motivo_rechazo = ? WHERE usuario_id = ?",
                estado, motivo, usuario.getId());
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud, String ip) {
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        });
        return solicitud;
    }

    private String token(String correo, String ip) throws Exception {
        var cuerpo = mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    private String tokenAdmin() throws Exception {
        return token(CORREO_ADMIN, IP_ADMIN);
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T con(T solicitud, String token, String ip) {
        desde(solicitud, ip);
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return solicitud;
    }

    private ResultActions cola(String token, String ip) throws Exception {
        return mvc.perform(con(get(COLA), token, ip));
    }

    private ResultActions expediente(String token, String ip, Long id) throws Exception {
        return mvc.perform(con(get("/api/admin/arrendadores/" + id + "/identidad"), token, ip));
    }

    private ResultActions aprobar(String token, String ip, Long id) throws Exception {
        return mvc.perform(con(post("/api/admin/arrendadores/" + id + "/identidad/aprobacion"), token, ip));
    }

    private ResultActions rechazar(String token, String ip, Long id, String cuerpo) throws Exception {
        return mvc.perform(con(post("/api/admin/arrendadores/" + id + "/identidad/rechazo"), token, ip)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String motivo(String texto) {
        return "{\"motivo\":\"" + texto + "\"}";
    }

    private List<ResultActions> losCuatroEndpoints(String token, String ip, Long id) throws Exception {
        return List.of(cola(token, ip), expediente(token, ip, id), aprobar(token, ip, id),
                rechazar(token, ip, id, motivo(MOTIVO)));
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    private List<Map<String, Object>> lista(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<List<Map<String, Object>>>() { });
    }

    private static String cuerpo(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private Map<String, Object> perfil(Usuario usuario) {
        return jdbc.queryForMap("SELECT estado_identidad, motivo_rechazo FROM perfil_arrendador WHERE usuario_id = ?",
                usuario.getId());
    }

    private List<Map<String, Object>> revisiones() {
        return jdbc.queryForList("SELECT * FROM revision_identidad ORDER BY id");
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

    private void sinCambios(Usuario arrendador) {
        assertThat(perfil(arrendador).get("estado_identidad")).isEqualTo("EN_REVISION");
        assertThat(revisiones()).isEmpty();
        assertThat(auditorias("IDENTIDAD_APROBADA")).isEmpty();
        assertThat(auditorias("IDENTIDAD_RECHAZADA")).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    // ── Control de IPs, sesión y rol ─────────────────────────────────────

    @Test
    @DisplayName("HU-06 R7, R40: desde una IP no permitida, con o sin sesión de admin, los 4 endpoints → 403 sin "
            + "ejecutar nada")
    void ipNoPermitidaResponde403() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var token = tokenAdmin();

        for (var conToken : new String[] {token, null}) {
            for (var resultado : losCuatroEndpoints(conToken, ipAjena, arrendador.getId())) {
                resultado.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO))
                        .andExpect(jsonPath("$.errores").isEmpty());
            }
        }
        sinCambios(arrendador);
    }

    @Test
    @DisplayName("HU-06 R8: desde una IP permitida sin sesión vigente los 4 endpoints → 401 de sesión inválida")
    void ipPermitidaSinSesionResponde401() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");

        for (var token : new String[] {null, "token-inventado-que-no-es-de-ninguna-sesion"}) {
            for (var resultado : losCuatroEndpoints(token, IP_ADMIN, arrendador.getId())) {
                resultado.andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
            }
        }
        sinCambios(arrendador);
    }

    @Test
    @DisplayName("HU-06 R9, R40: una sesión USUARIO o ARRENDADOR desde una IP permitida recibe 403 en los 4 endpoints")
    void rolDistintoDeAdminResponde403() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        crearUsuario("beto@correo.pe");

        for (var correo : new String[] {"beto@correo.pe", "ana@correo.pe"}) {
            var token = token(correo, IP_ADMIN);
            for (var resultado : losCuatroEndpoints(token, IP_ADMIN, arrendador.getId())) {
                resultado.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
            }
        }
        sinCambios(arrendador);
    }

    @Test
    @DisplayName("HU-06 R12: GET /api/archivos con una URL firmada responde 200 aunque llegue desde una IP no "
            + "permitida")
    void archivosNoSeFiltranPorIp() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var documentos = mapa(cuerpo(expediente(tokenAdmin(), IP_ADMIN, arrendador.getId())
                .andExpect(status().isOk()))).get("documentos");
        @SuppressWarnings("unchecked")
        var url = (String) ((List<Map<String, Object>>) documentos).getFirst().get("url");

        mvc.perform(desde(get(URI.create(url)), ipAjena)).andExpect(status().isOk());
        mvc.perform(desde(post("/api/sesiones"), ipAjena).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@correo.pe\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated());
    }

    // ── Cola ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R20, R21, R26: la cola lista a cada arrendador en revisión con 5 campos, del envío más "
            + "antiguo al más reciente y a igual fecha por id, con no-store")
    void colaOrdenadaPorFechaDeEnvio() throws Exception {
        var primero = crearArrendador("x@correo.pe", "Ximena");
        var segundo = crearArrendador("y@correo.pe", "Yolanda");
        var tercero = crearArrendador("z@correo.pe", "Zoila");
        enviarDocumentos("y@correo.pe");
        var envioY = reloj.instant();
        reloj.avanzar(Duration.ofMinutes(20));
        enviarDocumentos("z@correo.pe");
        enviarDocumentos("x@correo.pe");
        var envioXz = reloj.instant();

        var resultado = cola(tokenAdmin(), IP_ADMIN)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        var filas = lista(cuerpo(resultado));
        assertThat(filas).extracting(fila -> id(fila.get("arrendadorId")))
                .containsExactly(segundo.getId(), primero.getId(), tercero.getId());
        assertThat(filas).allSatisfy(fila -> assertThat(fila)
                .containsOnlyKeys("arrendadorId", "nombre", "razonSocial", "tipoDocumento", "enviadoEn"));
        var y = filas.getFirst();
        assertThat(y.get("nombre")).isEqualTo("Yolanda");
        assertThat(y.get("razonSocial")).isEqualTo("Razón de Yolanda");
        assertThat(y.get("tipoDocumento")).isEqualTo("RUC");
        assertThat(Instant.parse((String) y.get("enviadoEn"))).isEqualTo(envioY);
        assertThat(Instant.parse((String) filas.get(1).get("enviadoEn"))).isEqualTo(envioXz);
        assertThat(Instant.parse((String) filas.get(2).get("enviadoEn"))).isEqualTo(envioXz);
    }

    @Test
    @DisplayName("HU-06 R22: la cola excluye identidades VERIFICADA, RECHAZADA y NO_INICIADA y cuentas SUSPENDIDA")
    void colaExcluyeOtrosEstados() throws Exception {
        var enCola = enRevision("ana@correo.pe", "Ana");
        var verificada = enRevision("vero@correo.pe", "Vero");
        var rechazada = enRevision("rita@correo.pe", "Rita");
        crearArrendador("nina@correo.pe", "Nina");
        var suspendida = enRevision("susy@correo.pe", "Susy");
        crearUsuario("beto@correo.pe");
        forzarEstado(verificada, "VERIFICADA", null);
        forzarEstado(rechazada, "RECHAZADA", MOTIVO);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", suspendida.getId());

        var filas = lista(cuerpo(cola(tokenAdmin(), IP_ADMIN).andExpect(status().isOk())));

        assertThat(filas).extracting(fila -> id(fila.get("arrendadorId"))).containsExactly(enCola.getId());
    }

    @Test
    @DisplayName("HU-06 R20: sin arrendadores en revisión la cola es una lista vacía")
    void colaVacia() throws Exception {
        crearArrendador("nina@correo.pe", "Nina");

        cola(tokenAdmin(), IP_ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    // ── Expediente ───────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R23, R24, R26: el expediente trae solo los datos del arrendador, el número descifrado y un "
            + "documento por tipo con URL firmada de 5 minutos")
    void expedienteCompleto() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var enviado = reloj.instant();
        reloj.avanzar(Duration.ofMinutes(3));

        var resultado = expediente(tokenAdmin(), IP_ADMIN, arrendador.getId())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));

        var expediente = mapa(cuerpo(resultado));
        assertThat(expediente).containsOnlyKeys("arrendadorId", "nombre", "correo", "telefono", "tipoDocumento",
                "numeroDocumento", "razonSocial", "direccionFiscal", "estadoIdentidad", "enviadoEn", "documentos");
        assertThat(id(expediente.get("arrendadorId"))).isEqualTo(arrendador.getId());
        assertThat(expediente.get("nombre")).isEqualTo("Ana Pérez");
        assertThat(expediente.get("correo")).isEqualTo("ana@correo.pe");
        assertThat(expediente.get("telefono")).isEqualTo("987654321");
        assertThat(expediente.get("tipoDocumento")).isEqualTo("RUC");
        assertThat(expediente.get("numeroDocumento")).isEqualTo(NUMERO_DOCUMENTO);
        assertThat(jdbc.queryForObject("SELECT numero_documento FROM perfil_arrendador WHERE usuario_id = ?",
                String.class, arrendador.getId())).isNotEqualTo(NUMERO_DOCUMENTO);
        assertThat(expediente.get("razonSocial")).isEqualTo("Razón de Ana Pérez");
        assertThat(expediente.get("direccionFiscal")).isEqualTo("Av. Javier Prado 4600");
        assertThat(expediente.get("estadoIdentidad")).isEqualTo("EN_REVISION");
        assertThat(Instant.parse((String) expediente.get("enviadoEn"))).isEqualTo(enviado);
        @SuppressWarnings("unchecked")
        var documentos = (List<Map<String, Object>>) expediente.get("documentos");
        assertThat(documentos).extracting(documento -> documento.get("tipo"))
                .containsExactly("IDENTIDAD", "PROPIEDAD");
        for (var documento : documentos) {
            assertThat(documento).containsOnlyKeys("id", "tipo", "tipoMime", "tamano", "subidoEn", "url",
                    "urlExpiraEn");
            assertThat(Instant.parse((String) documento.get("urlExpiraEn")))
                    .isEqualTo(reloj.instant().plus(Duration.ofMinutes(5)));
            var url = (String) documento.get("url");
            assertThat(url).startsWith("/api/archivos?").contains("expira=" + reloj.instant().plus(
                    Duration.ofMinutes(5)).getEpochSecond());
            mvc.perform(desde(get(URI.create(url)), IP_ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, (String) documento.get("tipoMime")));
        }
    }

    @Test
    @DisplayName("HU-06 R25: el expediente de un arrendador fuera de la cola, de un USUARIO o de un id inexistente "
            + "→ 404 sin firmar URL")
    void expedienteFueraDeLaColaDa404() throws Exception {
        var verificada = enRevision("vero@correo.pe", "Vero");
        forzarEstado(verificada, "VERIFICADA", null);
        var usuario = crearUsuario("beto@correo.pe");
        var token = tokenAdmin();

        for (var id : new Long[] {verificada.getId(), usuario.getId(), admin.getId(), 999_999L}) {
            var resultado = expediente(token, IP_ADMIN, id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            assertThat(cuerpo(resultado)).doesNotContain("/api/archivos").doesNotContain(NUMERO_DOCUMENTO);
        }
    }

    // ── Aprobación ───────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R27, R28, R29, R30: aprobar → 201 con decisión, estado y fecha; perfil VERIFICADA, revisión "
            + "del admin de la sesión y un aviso por correo")
    void aprobarIdentidad() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Pérez");
        var tokenArrendador = enviarDocumentos("ana@correo.pe");
        var otro = crearUsuario("otro@correo.pe");
        var token = tokenAdmin();

        var resultado = mvc.perform(con(post("/api/admin/arrendadores/" + arrendador.getId()
                                + "/identidad/aprobacion"), token, IP_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":" + otro.getId() + ",\"decision\":\"RECHAZADA\"}"))
                .andExpect(status().isCreated());

        var respuesta = mapa(cuerpo(resultado));
        assertThat(respuesta).containsOnlyKeys("decision", "estadoIdentidad", "fecha");
        assertThat(respuesta.get("decision")).isEqualTo("APROBADA");
        assertThat(respuesta.get("estadoIdentidad")).isEqualTo("VERIFICADA");
        assertThat(Instant.parse((String) respuesta.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(perfil(arrendador).get("estado_identidad")).isEqualTo("VERIFICADA");
        assertThat(perfil(arrendador).get("motivo_rechazo")).isNull();
        var fila = revisiones().getFirst();
        assertThat(revisiones()).hasSize(1);
        assertThat(id(fila.get("arrendador_id"))).isEqualTo(arrendador.getId());
        assertThat(id(fila.get("admin_id"))).isEqualTo(admin.getId());
        assertThat(fila.get("decision")).isEqualTo("APROBADA");
        assertThat(fila.get("motivo")).isNull();
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(notificador.avisos()).containsExactly(new Aviso("APROBADA", "ana@correo.pe", null));
        mvc.perform(con(get("/api/sesiones/actual"), tokenArrendador, ipAjena))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").isEmpty());
    }

    // ── Rechazo ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R31, R32, R33, R34: rechazar con motivo → 201; perfil RECHAZADA con el motivo recortado, "
            + "revisión y un aviso con el motivo")
    void rechazarIdentidad() throws Exception {
        var arrendador = crearArrendador("ana@correo.pe", "Ana Pérez");
        var tokenArrendador = enviarDocumentos("ana@correo.pe");

        var resultado = rechazar(tokenAdmin(), IP_ADMIN, arrendador.getId(), motivo("   " + MOTIVO + "  "))
                .andExpect(status().isCreated());

        var respuesta = mapa(cuerpo(resultado));
        assertThat(respuesta).containsOnlyKeys("decision", "estadoIdentidad", "fecha");
        assertThat(respuesta.get("decision")).isEqualTo("RECHAZADA");
        assertThat(respuesta.get("estadoIdentidad")).isEqualTo("RECHAZADA");
        assertThat(Instant.parse((String) respuesta.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(perfil(arrendador).get("estado_identidad")).isEqualTo("RECHAZADA");
        assertThat(perfil(arrendador).get("motivo_rechazo")).isEqualTo(MOTIVO);
        var fila = revisiones().getFirst();
        assertThat(revisiones()).hasSize(1);
        assertThat(id(fila.get("arrendador_id"))).isEqualTo(arrendador.getId());
        assertThat(id(fila.get("admin_id"))).isEqualTo(admin.getId());
        assertThat(fila.get("decision")).isEqualTo("RECHAZADA");
        assertThat(fila.get("motivo")).isEqualTo(MOTIVO);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(notificador.avisos()).containsExactly(new Aviso("RECHAZADA", "ana@correo.pe", MOTIVO));
        mvc.perform(con(get("/api/sesiones/actual"), tokenArrendador, ipAjena))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value(AVISO_RECHAZADA + MOTIVO));
    }

    @Test
    @DisplayName("HU-06 R35, R40: sin motivo, vacío, solo espacios o de 501 caracteres → 400 en el campo motivo sin "
            + "cambios")
    void motivoInvalidoResponde400() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var token = tokenAdmin();

        for (var cuerpo : new String[] {"{}", "{\"motivo\":null}", motivo(""), motivo("   "),
                motivo("a".repeat(501))}) {
            rechazar(token, IP_ADMIN, arrendador.getId(), cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("motivo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(M_MOTIVO));
        }
        mvc.perform(con(post("/api/admin/arrendadores/" + arrendador.getId() + "/identidad/rechazo"), token,
                        IP_ADMIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION));
        sinCambios(arrendador);
        rechazar(token, IP_ADMIN, arrendador.getId(), motivo("a".repeat(500))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-06 R36: la BD rechaza una decisión desconocida, un RECHAZADA sin motivo o en blanco y un "
            + "APROBADA con motivo")
    void restriccionesDeLaTabla() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var insertar = "INSERT INTO revision_identidad (arrendador_id, admin_id, decision, motivo, fecha) "
                + "VALUES (?, ?, ?, ?, now())";

        for (var invalida : List.of(new String[] {"PENDIENTE", null}, new String[] {"RECHAZADA", null},
                new String[] {"RECHAZADA", "   "}, new String[] {"APROBADA", "Todo bien"})) {
            assertThatThrownBy(() -> jdbc.update(insertar, arrendador.getId(), admin.getId(), invalida[0], invalida[1]))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update(insertar, arrendador.getId(), admin.getId(), "APROBADA", null);
        jdbc.update(insertar, arrendador.getId(), admin.getId(), "RECHAZADA", MOTIVO);
        assertThat(revisiones()).hasSize(2);
    }

    // ── Estado y concurrencia ────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R37, R40: decidir sobre un id inexistente, un USUARIO, el admin o un arrendador SUSPENDIDA → "
            + "404 sin cambios")
    void decisionSobreArrendadorNoValido() throws Exception {
        var suspendido = enRevision("susy@correo.pe", "Susy");
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", suspendido.getId());
        var usuario = crearUsuario("beto@correo.pe");
        var token = tokenAdmin();

        for (var id : new Long[] {999_999L, usuario.getId(), admin.getId(), suspendido.getId()}) {
            aprobar(token, IP_ADMIN, id).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            rechazar(token, IP_ADMIN, id, motivo(MOTIVO)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }
        sinCambios(suspendido);
    }

    @Test
    @DisplayName("HU-06 R38, R40: decidir sobre una identidad que no está EN_REVISION → 400 ya revisada sin cambios")
    void decisionYaTomada() throws Exception {
        var verificada = enRevision("vero@correo.pe", "Vero");
        var rechazada = enRevision("rita@correo.pe", "Rita");
        var noIniciada = crearArrendador("nina@correo.pe", "Nina");
        forzarEstado(verificada, "VERIFICADA", null);
        forzarEstado(rechazada, "RECHAZADA", MOTIVO);
        var token = tokenAdmin();

        for (var arrendador : List.of(verificada, rechazada, noIniciada)) {
            aprobar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(YA_REVISADA))
                    .andExpect(jsonPath("$.errores").isEmpty());
            rechazar(token, IP_ADMIN, arrendador.getId(), motivo("Otro motivo")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(YA_REVISADA));
        }
        assertThat(perfil(verificada).get("estado_identidad")).isEqualTo("VERIFICADA");
        assertThat(perfil(rechazada).get("motivo_rechazo")).isEqualTo(MOTIVO);
        assertThat(perfil(noIniciada).get("estado_identidad")).isEqualTo("NO_INICIADA");
        assertThat(revisiones()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    @Test
    @DisplayName("HU-06 R38: una segunda decisión tras aprobar responde 400 ya revisada")
    void segundaDecisionTrasAprobar() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var token = tokenAdmin();
        aprobar(token, IP_ADMIN, arrendador.getId()).andExpect(status().isCreated());

        rechazar(token, IP_ADMIN, arrendador.getId(), motivo(MOTIVO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(YA_REVISADA));

        assertThat(perfil(arrendador).get("estado_identidad")).isEqualTo("VERIFICADA");
        assertThat(revisiones()).hasSize(1);
        assertThat(notificador.avisos()).hasSize(1);
    }

    @Test
    @DisplayName("HU-06 R39: dos decisiones simultáneas sobre el mismo arrendador → una 201 y otra 400, una revisión "
            + "y un aviso")
    void decisionesSimultaneas() throws Exception {
        var arrendador = enRevision("ana@correo.pe", "Ana Pérez");
        var token = tokenAdmin();
        var listos = new CountDownLatch(1);
        Callable<Integer> aprobarALaVez = () -> {
            listos.await();
            return aprobar(token, IP_ADMIN, arrendador.getId()).andReturn().getResponse().getStatus();
        };
        Callable<Integer> rechazarALaVez = () -> {
            listos.await();
            return rechazar(token, IP_ADMIN, arrendador.getId(), motivo(MOTIVO)).andReturn().getResponse().getStatus();
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(aprobarALaVez);
            var b = hilos.submit(rechazarALaVez);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 400);
        }

        assertThat(revisiones()).hasSize(1);
        assertThat(notificador.avisos()).hasSize(1);
        var ganadora = (String) revisiones().getFirst().get("decision");
        assertThat(perfil(arrendador).get("estado_identidad"))
                .isEqualTo("APROBADA".equals(ganadora) ? "VERIFICADA" : "RECHAZADA");
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R42, R43: cada decisión se audita con el admin, el perfil, la IP, la fecha y "
            + "decision=…;revision=…, sin motivo, número, correo ni URL")
    void auditaCadaDecision() throws Exception {
        var aprobado = enRevision("ana@correo.pe", "Ana Pérez");
        var rechazado = enRevision("rita@correo.pe", "Rita");
        var token = tokenAdmin();
        expediente(token, IP_ADMIN, aprobado.getId()).andExpect(status().isOk());

        aprobar(token, IP_ADMIN, aprobado.getId()).andExpect(status().isCreated());
        rechazar(token, IP_ADMIN, rechazado.getId(), motivo(MOTIVO)).andExpect(status().isCreated());

        var revisiones = revisiones();
        var aprobadas = auditorias("IDENTIDAD_APROBADA");
        var rechazadas = auditorias("IDENTIDAD_RECHAZADA");
        assertThat(aprobadas).hasSize(1);
        assertThat(rechazadas).hasSize(1);
        verificarAuditoria(aprobadas.getFirst(), aprobado, "decision=APROBADA;revision=" + revisiones.get(0).get("id"));
        verificarAuditoria(rechazadas.getFirst(), rechazado,
                "decision=RECHAZADA;revision=" + revisiones.get(1).get("id"));
        assertThat(jdbc.queryForList("SELECT detalle FROM auditoria", String.class)).allSatisfy(detalle ->
                assertThat(String.valueOf(detalle)).doesNotContain(MOTIVO).doesNotContain("borrosa")
                        .doesNotContain(NUMERO_DOCUMENTO).doesNotContain("@correo.pe")
                        .doesNotContain("/api/archivos"));
    }

    private void verificarAuditoria(Map<String, Object> fila, Usuario arrendador, String detalle) {
        assertThat(id(fila.get("usuario_id"))).isEqualTo(admin.getId());
        assertThat(fila.get("entidad")).isEqualTo("perfil_arrendador");
        assertThat(id(fila.get("entidad_id"))).isEqualTo(arrendador.getId());
        assertThat(fila.get("ip")).isEqualTo(IP_ADMIN);
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
        assertThat(fila.get("detalle")).isEqualTo(detalle);
    }
}
