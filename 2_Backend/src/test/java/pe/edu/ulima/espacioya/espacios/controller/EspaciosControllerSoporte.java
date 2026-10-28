package pe.edu.ulima.espacioya.espacios.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las IT de HU-07. Todas comparten esta configuración, así Spring reutiliza un solo contexto (y un
 * solo PostgreSQL). Las imágenes se generan en memoria ({@code ArchivosDePrueba}); ninguna vive en el repo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, EspaciosControllerSoporte.Dobles.class})
abstract class EspaciosControllerSoporte {

    static final String ESPACIOS = "/api/arrendadores/actual/espacios";
    static final String ZONAS = "/api/zonas";
    static final String FOTOS_PUBLICAS = "/api/fotos/";
    static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    static final String DESCRIPCION = "Cancha de grass sintético techada, con vestuarios.";
    static final String CONTRASENA = "Segura123!";
    static final String CORREO = "ana@correo.pe";
    static final String OTRO_CORREO = "beto@correo.pe";
    static final String CORREO_USUARIO = "carla@correo.pe";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_DOCUMENTO = new AtomicInteger(1);
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
    @Value("${espacioya.almacenamiento.directorio}")
    Path directorio;

    String ip;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @BeforeEach
    void prepararBase() throws IOException {
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        vaciarDirectorio();
        reloj.avanzar(Duration.ofHours(1));
        ip = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
    }

    // HU-01…HU-05 usan 10.1.x.x a 10.5.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.7." + (n / 250) + "." + (n % 250 + 1);
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

    Usuario crearArrendador(String correo) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, "Ana Pérez", "987654321",
                "2026-10", reloj.instant()));
        var hash = String.format("%064d", SIGUIENTE_DOCUMENTO.getAndIncrement());
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970", hash,
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        return usuario;
    }

    Usuario crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant()));
    }

    void forzarIdentidad(Usuario usuario, String estado) {
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = ? WHERE usuario_id = ?", estado, usuario.getId());
    }

    void forzarEstado(long espacioId, String estado) {
        jdbc.update("UPDATE espacio SET estado = ? WHERE id = ?", estado, espacioId);
    }

    void quitarCorreoVerificado(Usuario usuario) {
        jdbc.update("UPDATE usuario SET correo_verificado = false WHERE id = ?", usuario.getId());
    }

    static Map<String, Object> ficha() {
        var ficha = new LinkedHashMap<String, Object>();
        ficha.put("nombre", "Cancha Los Olivos");
        ficha.put("tipo", "CANCHA");
        ficha.put("direccion", "Av. Universitaria 1234");
        ficha.put("zona", "LOS_OLIVOS");
        ficha.put("capacidad", 20);
        ficha.put("precioHora", 80);
        ficha.put("descripcion", DESCRIPCION);
        return ficha;
    }

    static Map<String, Object> ficha(String campo, Object valor) {
        var ficha = ficha();
        ficha.put(campo, valor);
        return ficha;
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud) {
        var origen = ip;
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
        return desde(solicitud);
    }

    String token(String correo) throws Exception {
        var cuerpo = mvc.perform(desde(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    ResultActions crear(String token, Map<String, Object> ficha) throws Exception {
        return mvc.perform(con(token, post(ESPACIOS)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ficha)));
    }

    long crearEspacio(String token) throws Exception {
        var cuerpo = crear(token, ficha()).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) mapa(cuerpo).get("id")).longValue();
    }

    ResultActions editar(String token, long id, Map<String, Object> ficha) throws Exception {
        return mvc.perform(con(token, put(ESPACIOS + "/" + id)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ficha)));
    }

    ResultActions consultar(String token, long id) throws Exception {
        return mvc.perform(con(token, get(ESPACIOS + "/" + id)));
    }

    ResultActions listar(String token) throws Exception {
        return mvc.perform(con(token, get(ESPACIOS)));
    }

    ResultActions publicar(String token, long id) throws Exception {
        return mvc.perform(con(token, post(ESPACIOS + "/" + id + "/publicacion")));
    }

    ResultActions subir(String token, long id, MockMultipartFile... partes) throws Exception {
        var solicitud = multipart(ESPACIOS + "/" + id + "/fotos");
        for (var parte : partes) {
            solicitud.file(parte);
        }
        return mvc.perform(con(token, solicitud));
    }

    static MockMultipartFile foto(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("foto", nombre, tipo, contenido);
    }

    ResultActions quitar(String token, long id, long fotoId) throws Exception {
        return mvc.perform(con(token, delete(ESPACIOS + "/" + id + "/fotos/" + fotoId)));
    }

    ResultActions verFoto(String token, String uuid) throws Exception {
        return mvc.perform(con(token, get(FOTOS_PUBLICAS + uuid)));
    }

    Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    List<Map<String, Object>> lista(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<List<Map<String, Object>>>() { });
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fotosDe(String cuerpo) {
        return (List<Map<String, Object>>) mapa(cuerpo).get("fotos");
    }

    // ── Consultas ────────────────────────────────────────────────────────

    List<Map<String, Object>> espacios() {
        return jdbc.queryForList("SELECT * FROM espacio ORDER BY id");
    }

    Map<String, Object> espacio(long id) {
        return jdbc.queryForMap("SELECT * FROM espacio WHERE id = ?", id);
    }

    List<Map<String, Object>> fotosEnBd(long espacioId) {
        return jdbc.queryForList("SELECT * FROM foto_espacio WHERE espacio_id = ? ORDER BY orden", espacioId);
    }

    List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    List<Map<String, Object>> todasLasAuditorias() {
        return jdbc.queryForList("SELECT * FROM auditoria ORDER BY id");
    }

    List<Path> archivosAlmacenados() throws IOException {
        if (!Files.exists(directorio)) {
            return List.of();
        }
        try (Stream<Path> rutas = Files.walk(directorio)) {
            return rutas.filter(Files::isRegularFile).toList();
        }
    }

    static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }
}
