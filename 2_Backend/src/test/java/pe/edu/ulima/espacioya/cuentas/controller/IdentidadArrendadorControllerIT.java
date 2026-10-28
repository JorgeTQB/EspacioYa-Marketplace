package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorArchivos;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.IdentidadArrendadorService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IdentidadArrendadorControllerIT.Dobles.class})
class IdentidadArrendadorControllerIT {

    private static final String DOCUMENTOS = "/api/arrendadores/actual/documentos";
    private static final String IDENTIDAD = "/api/arrendadores/actual/identidad";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String ESTADO_NO_PERMITIDO = "Ya enviaste tus documentos o tu identidad ya está verificada.";
    private static final String TAMANO_EXCEDIDO = "El archivo debe pesar como máximo 5 MB.";
    private static final String TIPO_NO_PERMITIDO = "El archivo debe ser PDF, JPG o PNG.";
    private static final String IMAGEN_ILEGIBLE =
            "No pudimos leer la imagen. Revisa que no esté dañada y vuelve a subirla.";
    private static final String FALTA_PROPIEDAD =
            "Sube el documento que acredita la propiedad o autorización del espacio.";
    private static final String FALTA_IDENTIDAD = "Sube tu documento de identidad (DNI o RUC).";
    private static final String AVISO_EN_REVISION =
            "Estamos revisando tus documentos de identidad. Te avisaremos cuando terminemos.";
    private static final String AVISO_RECHAZADA = "Tu identidad fue rechazada. Vuelve a subir tus documentos. Motivo: ";
    private static final String MOTIVO = "La foto del DNI está borrosa.";
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final String CORREO = "ana@correo.pe";
    private static final String OTRO_CORREO = "beto@correo.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String NUMERO_DOCUMENTO = "20100070970";
    private static final String NOMBRE_IDENTIDAD = "mi-dni-ana-perez.pdf";
    private static final String NOMBRE_PROPIEDAD = "titulo-cancha-ana.png";
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
    @Autowired
    private CifradorArchivos cifrador;
    @Autowired
    private IdentidadArrendadorService servicio;
    @Value("${espacioya.almacenamiento.directorio}")
    private Path directorio;

    private String ip;
    private final byte[] pdf = ArchivosDePrueba.pdf(4096);
    private final byte[] png = ArchivosDePrueba.png(60, 40);

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @BeforeEach
    void preparar() throws IOException {
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

    // HU-01 usa 10.1.x.x, HU-02 10.2.x.x y HU-03 10.3.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.5." + (n / 250) + "." + (n % 250 + 1);
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

    private Usuario crearArrendador(String correo) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, "Ana Pérez", "987654321",
                "2026-10", reloj.instant()));
        var hash = (correo.equals(CORREO) ? "c" : "d").repeat(64);
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, NUMERO_DOCUMENTO, hash,
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        return usuario;
    }

    private void forzarEstado(Usuario usuario, String estado, String motivo) {
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = ?, motivo_rechazo = ? WHERE usuario_id = ?",
                estado, motivo, usuario.getId());
    }

    private static MockMultipartFile parte(String nombre, String archivo, String tipo, byte[] contenido) {
        return new MockMultipartFile(nombre, archivo, tipo, contenido);
    }

    private static MockMultipartFile identidad(byte[] contenido) {
        return parte("documentoIdentidad", NOMBRE_IDENTIDAD, "application/pdf", contenido);
    }

    private static MockMultipartFile propiedad(byte[] contenido) {
        return parte("documentoPropiedad", NOMBRE_PROPIEDAD, "image/png", contenido);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud) {
        var origen = ip;
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
        return solicitud;
    }

    private String token(String correo) throws Exception {
        var cuerpo = mvc.perform(desde(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    private ResultActions enviar(String token, MockMultipartFile... partes) throws Exception {
        MockMultipartHttpServletRequestBuilder solicitud = multipart(DOCUMENTOS);
        for (var parte : partes) {
            solicitud.file(parte);
        }
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(desde(solicitud));
    }

    private ResultActions enviarValidos(String token) throws Exception {
        return enviar(token, identidad(pdf), propiedad(png));
    }

    private ResultActions consultar(String token) throws Exception {
        var solicitud = get(IDENTIDAD);
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(desde(solicitud));
    }

    private ResultActions abrir(String url) throws Exception {
        return mvc.perform(desde(get(URI.create(url))));
    }

    private Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> documentosDe(String cuerpo) {
        return (List<Map<String, Object>>) mapa(cuerpo).get("documentos");
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private List<Map<String, Object>> filas() {
        return jdbc.queryForList("SELECT * FROM documento_identidad ORDER BY tipo");
    }

    private Map<String, Object> perfilDe(Usuario usuario) {
        return jdbc.queryForMap("SELECT estado_identidad, motivo_rechazo FROM perfil_arrendador WHERE usuario_id = ?",
                usuario.getId());
    }

    private List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    private List<Path> archivosAlmacenados() throws IOException {
        if (!Files.exists(directorio)) {
            return List.of();
        }
        try (Stream<Path> rutas = Files.walk(directorio)) {
            return rutas.filter(Files::isRegularFile).toList();
        }
    }

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    private void sinCambios(Usuario usuario, String estado) throws IOException {
        assertThat(perfilDe(usuario).get("estado_identidad")).isEqualTo(estado);
        assertThat(filas()).isEmpty();
        assertThat(archivosAlmacenados()).isEmpty();
    }

    // ── Envío aceptado (CA1) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-05 R1, R2, R3: un envío válido responde 201 EN_REVISION, cambia el perfil y guarda dos filas")
    void envioAceptado() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);

        var cuerpo = enviarValidos(token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoIdentidad").value("EN_REVISION"))
                .andExpect(jsonPath("$.motivoRechazo").isEmpty())
                .andExpect(jsonPath("$.puedeEnviar").value(false))
                .andExpect(jsonPath("$.documentos.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(mapa(cuerpo)).containsOnlyKeys("estadoIdentidad", "motivoRechazo", "puedeEnviar", "documentos");
        assertThat(perfilDe(ana).get("estado_identidad")).isEqualTo("EN_REVISION");
        var filas = filas();
        assertThat(filas).hasSize(2);
        assertThat(filas).extracting(f -> f.get("arrendador_id")).containsOnly(ana.getId());
        assertThat(filas).extracting(f -> f.get("tipo")).containsExactly("IDENTIDAD", "PROPIEDAD");
        assertThat(filas).extracting(f -> f.get("tipo_mime")).containsExactly("application/pdf", "image/png");
        assertThat(filas).extracting(f -> f.get("tamano")).containsExactly(pdf.length, png.length);
        assertThat(filas).extracting(f -> instante(f.get("subido_en"))).containsOnly(reloj.instant());
    }

    @Test
    @DisplayName("HU-05 R4: un envío desde RECHAZADA deja motivo_rechazo nulo")
    void envioDesdeRechazadaLimpiaMotivo() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarEstado(ana, "RECHAZADA", MOTIVO);

        enviarValidos(token(CORREO)).andExpect(status().isCreated());

        assertThat(perfilDe(ana).get("estado_identidad")).isEqualTo("EN_REVISION");
        assertThat(perfilDe(ana).get("motivo_rechazo")).isNull();
    }

    @Test
    @DisplayName("HU-05 R5: reenviar tras un rechazo reemplaza las filas y borra los archivos anteriores")
    void reenvioReemplaza() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        enviarValidos(token).andExpect(status().isCreated());
        var anteriores = archivosAlmacenados();
        var idsAnteriores = filas().stream().map(f -> f.get("id")).toList();
        assertThat(anteriores).hasSize(2);
        forzarEstado(ana, "RECHAZADA", MOTIVO);

        enviar(token, identidad(ArchivosDePrueba.pdf(2048)), propiedad(ArchivosDePrueba.jpeg(30, 30)))
                .andExpect(status().isCreated());

        var filas = filas();
        assertThat(filas).hasSize(2);
        assertThat(filas).extracting(f -> f.get("id")).doesNotContainAnyElementsOf(idsAnteriores);
        assertThat(filas).extracting(f -> f.get("tipo_mime")).containsExactly("application/pdf", "image/jpeg");
        for (var anterior : anteriores) {
            assertThat(Files.exists(anterior)).isFalse();
        }
        assertThat(archivosAlmacenados()).hasSize(2);
    }

    @Test
    @DisplayName("HU-05 R6: un archivo de exactamente 5 242 880 bytes se acepta")
    void limiteExactoSeAcepta() throws Exception {
        crearArrendador(CORREO);

        enviar(token(CORREO), identidad(ArchivosDePrueba.pdf(5_242_880)), propiedad(png))
                .andExpect(status().isCreated());

        assertThat(filas().getFirst().get("tamano")).isEqualTo(5_242_880);
    }

    @Test
    @DisplayName("HU-05 R7: los documentos se asocian al usuario de la sesión aunque se envíe otro arrendadorId")
    void ignoraArrendadorIdEnviado() throws Exception {
        var ana = crearArrendador(CORREO);
        var beto = crearArrendador(OTRO_CORREO);
        var token = token(CORREO);

        var solicitud = multipart(DOCUMENTOS).file(identidad(pdf)).file(propiedad(png))
                .file(parte("arrendadorId", "x.txt", "text/plain", beto.getId().toString().getBytes()))
                .param("arrendadorId", beto.getId().toString())
                .param("usuarioId", beto.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mvc.perform(desde(solicitud)).andExpect(status().isCreated());

        assertThat(filas()).extracting(f -> f.get("arrendador_id")).containsOnly(ana.getId());
        assertThat(perfilDe(beto).get("estado_identidad")).isEqualTo("NO_INICIADA");
        assertThat(perfilDe(ana).get("estado_identidad")).isEqualTo("EN_REVISION");
    }

    @Test
    @DisplayName("HU-05 R10, R11: un PNG declarado como x.pdf y application/pdf se acepta con tipo_mime image/png")
    void tipoMimeSaleDelContenido() throws Exception {
        crearArrendador(CORREO);

        enviar(token(CORREO), identidad(pdf), parte("documentoPropiedad", "x.pdf", "application/pdf", png))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentos[1].tipoMime").value("image/png"));

        assertThat(filas().get(1).get("tipo_mime")).isEqualTo("image/png");
    }

    @Test
    @DisplayName("HU-05 R26, R27: la respuesta y los datos guardados no llevan el nombre original ni el número")
    void sinNombreOriginalNiNumero() throws Exception {
        crearArrendador(CORREO);

        var cuerpo = enviarValidos(token(CORREO)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain(NOMBRE_IDENTIDAD, NOMBRE_PROPIEDAD, "mi-dni", NUMERO_DOCUMENTO);
        for (var fila : filas()) {
            assertThat((String) fila.get("ruta_archivo")).matches("^identidad/" + UUID + "$");
            assertThat(fila.values()).noneMatch(valor -> String.valueOf(valor).contains("mi-dni")
                    || String.valueOf(valor).contains("titulo-cancha"));
        }
        for (var archivo : archivosAlmacenados()) {
            var relativa = directorio.relativize(archivo).toString().replace('\\', '/');
            assertThat(relativa).matches("^identidad/" + UUID + "$");
        }
    }

    @Test
    @DisplayName("HU-05 R30, R33: en disco los bytes están cifrados y al descifrarlos el PDF es idéntico")
    void archivoCifradoEnDisco() throws Exception {
        crearArrendador(CORREO);

        enviarValidos(token(CORREO)).andExpect(status().isCreated());

        var ruta = directorio.resolve((String) filas().getFirst().get("ruta_archivo"));
        var guardado = Files.readAllBytes(ruta);
        assertThat(guardado).isNotEqualTo(pdf);
        assertThat(new String(guardado, 0, 5, StandardCharsets.ISO_8859_1)).isNotEqualTo("%PDF-");
        assertThat(ArchivosDePrueba.contiene(guardado, "%PDF")).isFalse();
        assertThat(cifrador.descifrar(guardado)).isEqualTo(pdf);
    }

    @Test
    @DisplayName("HU-05 R32: la imagen almacenada es la versión vuelta a codificar, sin el bloque de texto PNG")
    void imagenAlmacenadaSinMetadatos() throws Exception {
        crearArrendador(CORREO);

        enviar(token(CORREO), identidad(pdf), propiedad(ArchivosDePrueba.pngConTexto()))
                .andExpect(status().isCreated());

        var ruta = directorio.resolve((String) filas().get(1).get("ruta_archivo"));
        var descifrado = cifrador.descifrar(Files.readAllBytes(ruta));
        assertThat(ArchivosDePrueba.contiene(descifrado, "tEXt")).isFalse();
        assertThat(ArchivosDePrueba.contiene(descifrado, ArchivosDePrueba.SECRETO_PNG)).isFalse();
    }

    @Test
    @DisplayName("HU-05 R31: la tabla documento_identidad no tiene columnas binarias")
    void sinColumnasBinarias() {
        var tipos = jdbc.queryForList("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'documento_identidad'", String.class);

        assertThat(tipos).isNotEmpty().doesNotContain("bytea", "oid");
    }

    // ── Archivo no permitido (CA2) ───────────────────────────────────────

    @Test
    @DisplayName("HU-05 R8, R14: si falta una parte responde 400 en ese campo y no cambia nada")
    void faltaUnaParte() throws Exception {
        var ana = crearArrendador(CORREO);

        enviar(token(CORREO), identidad(pdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("documentoPropiedad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(FALTA_PROPIEDAD));

        sinCambios(ana, "NO_INICIADA");
    }

    @Test
    @DisplayName("HU-05 R8, R14: una parte de 0 bytes responde 400 en ese campo y no cambia nada")
    void parteVacia() throws Exception {
        var ana = crearArrendador(CORREO);

        enviar(token(CORREO), identidad(new byte[0]), propiedad(png))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("documentoIdentidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(FALTA_IDENTIDAD));

        sinCambios(ana, "NO_INICIADA");
    }

    @Test
    @DisplayName("HU-05 R9, R14: una parte de 5 242 881 bytes responde 400 con el mensaje de tamaño en su campo")
    void parteDemasiadoGrande() throws Exception {
        var ana = crearArrendador(CORREO);

        enviar(token(CORREO), identidad(ArchivosDePrueba.pdf(5_242_881)), propiedad(png))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("documentoIdentidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(TAMANO_EXCEDIDO));

        sinCambios(ana, "NO_INICIADA");
    }

    @Test
    @DisplayName("HU-05 R10, R14: un texto renombrado a .pdf y declarado application/pdf se rechaza por tipo")
    void tipoNoPermitido() throws Exception {
        var ana = crearArrendador(CORREO);

        enviar(token(CORREO), identidad(ArchivosDePrueba.texto()), propiedad(png))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("documentoIdentidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(TIPO_NO_PERMITIDO));

        sinCambios(ana, "NO_INICIADA");
    }

    @Test
    @DisplayName("HU-05 R12, R14: una imagen PNG ilegible responde 400 con el mensaje de imagen ilegible")
    void imagenIlegible() throws Exception {
        var ana = crearArrendador(CORREO);

        enviar(token(CORREO), identidad(pdf), propiedad(ArchivosDePrueba.pngIlegible()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("documentoPropiedad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(IMAGEN_ILEGIBLE));

        sinCambios(ana, "NO_INICIADA");
    }

    @Test
    @DisplayName("HU-05 R15, R14: dos partes inválidas responden 400 con un error por parte")
    void dosPartesInvalidas() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarEstado(ana, "RECHAZADA", MOTIVO);

        enviar(token(CORREO), identidad(ArchivosDePrueba.zip()), propiedad(ArchivosDePrueba.pdf(5_242_881)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("documentoIdentidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(TIPO_NO_PERMITIDO))
                .andExpect(jsonPath("$.errores[1].campo").value("documentoPropiedad"))
                .andExpect(jsonPath("$.errores[1].mensaje").value(TAMANO_EXCEDIDO));

        sinCambios(ana, "RECHAZADA");
        assertThat(perfilDe(ana).get("motivo_rechazo")).isEqualTo(MOTIVO);
    }

    // ── Estado y acceso ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-05 R16, R14: en EN_REVISION y en VERIFICADA el envío responde 400 con el mensaje de estado")
    void estadoNoPermitido() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        for (var estado : new String[] {"EN_REVISION", "VERIFICADA"}) {
            forzarEstado(ana, estado, null);

            enviarValidos(token)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(ESTADO_NO_PERMITIDO))
                    .andExpect(jsonPath("$.errores").isEmpty());

            sinCambios(ana, estado);
        }
    }

    @Test
    @DisplayName("HU-05 R16: dos envíos simultáneos del mismo arrendador, solo uno se acepta")
    void enviosSimultaneos() throws Exception {
        var ana = crearArrendador(CORREO);
        var usuario = new UsuarioAutenticado(ana.getId(), 1L, "ARRENDADOR");
        var listos = new CountDownLatch(1);
        Callable<Boolean> envio = () -> {
            listos.await();
            try {
                servicio.enviar(usuario, new ArchivoRecibido(pdf, pdf.length), new ArchivoRecibido(png, png.length),
                        ip);
                return true;
            } catch (ReglaNegocioException e) {
                return false;
            }
        };
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(envio);
            var b = hilos.submit(envio);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }

        assertThat(filas()).hasSize(2);
        assertThat(archivosAlmacenados()).hasSize(2);
    }

    @Test
    @DisplayName("HU-05 R17, R20: una cuenta USUARIO recibe 403 en el envío y en la consulta, sin almacenar nada")
    void usuarioNoArrendador() throws Exception {
        crearUsuario(CORREO);
        var token = token(CORREO);

        enviarValidos(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        consultar(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        assertThat(filas()).isEmpty();
        assertThat(archivosAlmacenados()).isEmpty();
        assertThat(auditorias(IdentidadArrendadorService.ACCION_CARGA_RECHAZADA)).isEmpty();
    }

    @Test
    @DisplayName("HU-05 R18, R20: un arrendador con correo no verificado recibe 403 sin almacenar nada")
    void correoNoVerificado() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        jdbc.update("UPDATE usuario SET correo_verificado = false WHERE id = ?", ana.getId());

        enviarValidos(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        sinCambios(ana, "NO_INICIADA");
        consultar(token).andExpect(status().isOk()).andExpect(jsonPath("$.puedeEnviar").value(false));
    }

    @Test
    @DisplayName("HU-05 R19: sin sesión el envío y la consulta responden 401 con el mensaje de sesión inválida")
    void sinSesion() throws Exception {
        var ana = crearArrendador(CORREO);

        enviarValidos(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        consultar(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        consultar("token-inventado")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));

        sinCambios(ana, "NO_INICIADA");
    }

    // ── Estado de identidad (CA3) ────────────────────────────────────────

    @Test
    @DisplayName("HU-05 R21, R22, R24: en RECHAZADA la consulta devuelve solo sus cuatro claves y el motivo")
    void consultaRechazada() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarEstado(ana, "RECHAZADA", MOTIVO);

        var cuerpo = consultar(token(CORREO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoIdentidad").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value(MOTIVO))
                .andExpect(jsonPath("$.puedeEnviar").value(true))
                .andExpect(jsonPath("$.documentos").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(mapa(cuerpo)).containsOnlyKeys("estadoIdentidad", "motivoRechazo", "puedeEnviar", "documentos");
    }

    @Test
    @DisplayName("HU-05 R23, R24: fuera de RECHAZADA el motivo es nulo y puedeEnviar sigue al estado")
    void consultaOtrosEstados() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var casos = Map.of("NO_INICIADA", true, "EN_REVISION", false, "VERIFICADA", false);
        for (var caso : casos.entrySet()) {
            forzarEstado(ana, caso.getKey(), "motivo viejo");

            consultar(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estadoIdentidad").value(caso.getKey()))
                    .andExpect(jsonPath("$.motivoRechazo").isEmpty())
                    .andExpect(jsonPath("$.puedeEnviar").value(caso.getValue()));
        }
    }

    @Test
    @DisplayName("HU-05 R25, R38: la consulta lista los documentos vigentes con solo sus campos y URL de 5 minutos")
    void consultaDocumentosVigentes() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        enviarValidos(token).andExpect(status().isCreated());

        var documentos = documentosDe(consultar(token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(documentos).hasSize(2);
        var filas = filas();
        for (int i = 0; i < 2; i++) {
            var documento = documentos.get(i);
            assertThat(documento).containsOnlyKeys("id", "tipo", "tipoMime", "tamano", "subidoEn", "url",
                    "urlExpiraEn");
            assertThat(((Number) documento.get("id")).longValue())
                    .isEqualTo(((Number) filas.get(i).get("id")).longValue());
            assertThat(documento.get("tipo")).isEqualTo(filas.get(i).get("tipo"));
            assertThat(Instant.parse((String) documento.get("subidoEn"))).isEqualTo(reloj.instant());
            assertThat((String) documento.get("url")).startsWith("/api/archivos?clave=");
            assertThat(Instant.parse((String) documento.get("urlExpiraEn")))
                    .isEqualTo(reloj.instant().plus(Duration.ofMinutes(5)));
        }
        assertThat(documentos.get(0).get("tipoMime")).isEqualTo("application/pdf");
        assertThat(((Number) documentos.get(1).get("tamano")).intValue()).isEqualTo(png.length);
    }

    @Test
    @DisplayName("HU-05 R28, R29: el aviso de la sesión cambia en EN_REVISION y en RECHAZADA incluye el motivo")
    void avisoDeSesion() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);

        enviarValidos(token).andExpect(status().isCreated());
        mvc.perform(desde(get("/api/sesiones/actual")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value(AVISO_EN_REVISION));

        forzarEstado(ana, "RECHAZADA", MOTIVO);
        mvc.perform(desde(get("/api/sesiones/actual")).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value(AVISO_RECHAZADA + MOTIVO));
        mvc.perform(desde(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aviso").value(AVISO_RECHAZADA + MOTIVO));
    }

    // ── URL firmada ──────────────────────────────────────────────────────

    private List<Map<String, Object>> documentosEnviados() throws Exception {
        crearArrendador(CORREO);
        return documentosDe(enviarValidos(token(CORREO)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("HU-05 R34, R35: la URL firmada sirve el archivo descifrado sin sesión y con encabezados seguros")
    void urlFirmadaSirveElArchivo() throws Exception {
        var documentos = documentosEnviados();

        abrir((String) documentos.get(0).get("url"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"documento.pdf\""))
                .andExpect(content().bytes(pdf));
        abrir((String) documentos.get(1).get("url"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"documento.png\""));
    }

    @Test
    @DisplayName("HU-05 R36: con la firma, la clave o el tipo alterados la URL responde 404 genérico")
    void urlAlteradaResponde404() throws Exception {
        var documentos = documentosEnviados();
        var url = (String) documentos.get(0).get("url");
        var otraUrl = (String) documentos.get(1).get("url");
        var firma = url.substring(url.indexOf("&firma=") + 7);
        var otraFirma = (firma.charAt(0) == 'A' ? "B" : "A") + firma.substring(1);
        var claveOtra = otraUrl.substring(otraUrl.indexOf("clave="), otraUrl.indexOf("&tipo="));

        var alteradas = new ArrayList<String>();
        alteradas.add(url.replace("&firma=" + firma, "&firma=" + otraFirma));
        alteradas.add(url.replaceFirst("clave=[^&]+", claveOtra));
        alteradas.add(url.replace("tipo=application%2Fpdf", "tipo=image%2Fpng"));
        alteradas.add(url.replace("&firma=" + firma, ""));
        alteradas.add("/api/archivos?clave=..%2F..%2Fsecreto&tipo=application%2Fpdf&expira=9999999999&firma=" + firma);
        for (var alterada : alteradas) {
            abrir(alterada)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }
    }

    @Test
    @DisplayName("HU-05 R37: a los 5 minutos la URL firmada vence y responde 404")
    void urlVencidaResponde404() throws Exception {
        var url = (String) documentosEnviados().get(0).get("url");
        reloj.avanzar(Duration.ofMinutes(5).minusSeconds(1));
        abrir(url).andExpect(status().isOk());

        reloj.avanzar(Duration.ofSeconds(1));

        abrir(url).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-05 R40, R42: cada documento aceptado se audita con tipo, mime y tamaño, sin datos sensibles")
    void auditaCadaCarga() throws Exception {
        var ana = crearArrendador(CORREO);

        enviarValidos(token(CORREO)).andExpect(status().isCreated());

        var cargas = auditorias(IdentidadArrendadorService.ACCION_CARGA_DOCUMENTO);
        var filas = filas();
        assertThat(cargas).hasSize(2);
        for (int i = 0; i < 2; i++) {
            var carga = cargas.get(i);
            assertThat(carga.get("usuario_id")).isEqualTo(ana.getId());
            assertThat(carga.get("entidad")).isEqualTo("documento_identidad");
            assertThat(((Number) carga.get("entidad_id")).longValue())
                    .isEqualTo(((Number) filas.get(i).get("id")).longValue());
            assertThat(carga.get("ip")).isEqualTo(ip);
            assertThat(instante(carga.get("fecha"))).isEqualTo(reloj.instant());
        }
        assertThat(cargas).extracting(c -> c.get("detalle")).containsExactly(
                "tipo=IDENTIDAD;mime=application/pdf;tamano=" + pdf.length,
                "tipo=PROPIEDAD;mime=image/png;tamano=" + png.length);
        sinDatosSensibles(cargas, filas);
    }

    @Test
    @DisplayName("HU-05 R41, R42: los envíos rechazados por archivo o por estado se auditan con el motivo")
    void auditaRechazos() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);

        enviar(token, identidad(ArchivosDePrueba.texto()), propiedad(ArchivosDePrueba.pngIlegible()))
                .andExpect(status().isBadRequest());
        enviar(token, identidad(pdf)).andExpect(status().isBadRequest());
        forzarEstado(ana, "EN_REVISION", null);
        enviarValidos(token).andExpect(status().isBadRequest());

        var rechazos = auditorias(IdentidadArrendadorService.ACCION_CARGA_RECHAZADA);
        assertThat(rechazos).extracting(r -> r.get("detalle")).containsExactly(
                "documentoIdentidad=TIPO_NO_PERMITIDO;documentoPropiedad=IMAGEN_ILEGIBLE",
                "documentoPropiedad=VACIO",
                "estado=EN_REVISION");
        for (var rechazo : rechazos) {
            assertThat(rechazo.get("usuario_id")).isEqualTo(ana.getId());
            assertThat(rechazo.get("entidad")).isEqualTo("perfil_arrendador");
            assertThat(rechazo.get("entidad_id")).isEqualTo(ana.getId());
            assertThat(rechazo.get("ip")).isEqualTo(ip);
            assertThat(instante(rechazo.get("fecha"))).isEqualTo(reloj.instant());
        }
        sinDatosSensibles(rechazos, List.of());
    }

    private static void sinDatosSensibles(List<Map<String, Object>> auditorias, List<Map<String, Object>> filas) {
        for (var auditoria : auditorias) {
            var detalle = (String) auditoria.get("detalle");
            assertThat(detalle).doesNotContain(NOMBRE_IDENTIDAD, NOMBRE_PROPIEDAD, NUMERO_DOCUMENTO, "identidad/",
                    "%PDF");
            for (var fila : filas) {
                assertThat(detalle).doesNotContain((String) fila.get("ruta_archivo"));
            }
        }
    }
}
