package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorArchivos;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.CambioCorreoService;
import pe.edu.ulima.espacioya.cuentas.service.PerfilService;

/** HU-20 por la API: acceso, consulta, datos básicos y foto (CA1), y la confirmación pública (R34). */
@SpringBootTest
// Sin volcar las solicitudes de MockMvc: R46 revisa que la salida no tenga contraseñas ni datos personales.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, PerfilSoporte.Dobles.class})
class PerfilControllerIT extends PerfilSoporte {

    private static final String PERFIL = "/api/usuarios/actual/perfil";
    private static final String FOTO = "/api/usuarios/actual/foto";
    private static final String CAMBIOS_CORREO = "/api/usuarios/actual/cambios-correo";
    private static final String FACTURACION = "/api/arrendadores/actual/facturacion";
    private static final String CONFIRMACIONES = "/api/cambios-correo/confirmaciones";
    private static final String CUERPO_CORREO = "{\"correo\":\"nuevo.perfil@correo.pe\",\"contrasena\":\"" + CONTRASENA
            + "\"}";
    private static final String CUERPO_FACTURACION = "{\"tipoDocumento\":\"RUC\",\"numeroDocumento\":\"20131312955\","
            + "\"razonSocial\":\"Otra SAC\",\"direccionFiscal\":\"Av. Nueva 1\",\"contrasena\":\"" + CONTRASENA + "\"}";

    @Autowired
    private CifradorArchivos cifradorArchivos;
    @Value("${espacioya.almacenamiento.directorio}")
    private Path directorio;

    private ResultActions consultar(String sesion) throws Exception {
        return mvc.perform(con(sesion, get(PERFIL)));
    }

    private ResultActions guardarDatos(String sesion, String cuerpo) throws Exception {
        return mvc.perform(con(sesion, put(PERFIL)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions subirFoto(String sesion, String nombre, String tipo, byte[] bytes) throws Exception {
        var archivo = new MockMultipartFile("foto", nombre, tipo, bytes);
        return mvc.perform(con(sesion, multipart(HttpMethod.PUT, FOTO).file(archivo)));
    }

    private ResultActions verFoto(String sesion) throws Exception {
        return mvc.perform(con(sesion, get(FOTO)));
    }

    private ResultActions quitarFoto(String sesion) throws Exception {
        return mvc.perform(con(sesion, delete(FOTO)));
    }

    private String rutaFoto(long id) {
        return (String) filaUsuario(id).get("foto_ruta");
    }

    // ── Acceso (STRIDE E) ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R1: sin sesión, todas las rutas de perfil responden 401 sin leer ni escribir nada")
    void sinSesion401() throws Exception {
        var id = crearArrendador();
        var antes = filaUsuario(id);
        var png = ArchivosDePrueba.png(20, 20);

        consultar(null).andExpect(status().isUnauthorized());
        guardarDatos(null, "{\"nombre\":\"Intruso\",\"telefono\":\"912345678\"}").andExpect(status().isUnauthorized());
        subirFoto(null, "a.png", "image/png", png).andExpect(status().isUnauthorized());
        verFoto(null).andExpect(status().isUnauthorized());
        quitarFoto(null).andExpect(status().isUnauthorized());
        mvc.perform(con(null, post(CAMBIOS_CORREO)).contentType(MediaType.APPLICATION_JSON).content(CUERPO_CORREO))
                .andExpect(status().isUnauthorized());
        mvc.perform(con(null, put(FACTURACION)).contentType(MediaType.APPLICATION_JSON).content(CUERPO_FACTURACION))
                .andExpect(status().isUnauthorized());

        assertThat(filaUsuario(id)).isEqualTo(antes);
        assertThat(filaPerfil(id).get("razon_social").toString()).startsWith("v1:");
        assertThat(cifrador.descifrar(filaPerfil(id).get("razon_social").toString())).isEqualTo(RAZON);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token", Integer.class)).isZero();
        assertThat(notificador.cambios()).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R2: un ADMIN recibe 403 en perfil, foto y cambios de correo sin comprobar la contraseña")
    void admin403() throws Exception {
        var id = crearAdmin();
        var sesion = token(CORREO_ADMIN);
        var antes = filaUsuario(id);

        consultar(sesion).andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        guardarDatos(sesion, "{\"nombre\":\"Admin\",\"telefono\":\"912345678\"}").andExpect(status().isForbidden());
        subirFoto(sesion, "a.png", "image/png", ArchivosDePrueba.png(20, 20)).andExpect(status().isForbidden());
        verFoto(sesion).andExpect(status().isForbidden());
        quitarFoto(sesion).andExpect(status().isForbidden());
        mvc.perform(con(sesion, post(CAMBIOS_CORREO)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"nuevo.admin@correo.pe\",\"contrasena\":\"Incorrecta1\"}"))
                .andExpect(status().isForbidden());

        assertThat(filaUsuario(id)).isEqualTo(antes);
        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
        assertThat(auditorias("PERFIL_ACTUALIZADO")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-20 R3: un USUARIO o un ADMIN que envía facturación válida recibe 403 sin contar un fallo")
    void facturacionDeOtroRol403() throws Exception {
        crearUsuario(CORREO_USUARIO);
        crearAdmin();
        var cuerpoMalo = CUERPO_FACTURACION.replace(CONTRASENA, "Incorrecta1");

        for (var correo : new String[] {CORREO_USUARIO, CORREO_ADMIN}) {
            var sesion = token(correo);
            for (int i = 0; i < 6; i++) {
                mvc.perform(con(sesion, put(FACTURACION)).contentType(MediaType.APPLICATION_JSON).content(cuerpoMalo))
                        .andExpect(status().isForbidden());
            }
        }

        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
        assertThat(auditorias("FACTURACION_ACTUALIZADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R4: un id, rol, correo o estado en el cuerpo se ignoran y no tocan otra cuenta")
    void cuerpoNoEligeLaCuenta() throws Exception {
        var propia = crearUsuario(CORREO_USUARIO);
        var otra = crearArrendador();
        var antesOtra = filaUsuario(otra);
        var sesion = token(CORREO_USUARIO);

        guardarDatos(sesion, "{\"id\":" + otra + ",\"usuarioId\":" + otra + ",\"rol\":\"ADMIN\","
                + "\"correo\":\"robado@correo.pe\",\"estadoIdentidad\":\"VERIFICADA\",\"correoVerificado\":false,"
                + "\"nombre\":\"Carla\",\"telefono\":\"912345678\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.correo").value(CORREO_USUARIO))
                .andExpect(jsonPath("$.nombre").value("Carla"));

        var fila = filaUsuario(propia);
        assertThat(fila.get("rol")).isEqualTo("USUARIO");
        assertThat(fila.get("correo")).isEqualTo(CORREO_USUARIO);
        assertThat(fila.get("correo_verificado")).isEqualTo(true);
        assertThat(filaUsuario(otra)).isEqualTo(antesOtra);
    }

    // ── Consulta (CA1) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R5, R6, R7: el ARRENDADOR ve su perfil con la facturación enmascarada, sin datos internos y "
            + "con no-store")
    void consultaArrendador() throws Exception {
        crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        var respuesta = consultar(sesion).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.rol").value("ARRENDADOR"))
                .andExpect(jsonPath("$.nombre").value(NOMBRE_ARRENDADOR))
                .andExpect(jsonPath("$.telefono").value(TELEFONO_ARRENDADOR))
                .andExpect(jsonPath("$.correo").value(CORREO_ARRENDADOR))
                .andExpect(jsonPath("$.correoPendiente").doesNotExist())
                .andExpect(jsonPath("$.tieneFoto").value(false))
                .andExpect(jsonPath("$.facturacion.tipoDocumento").value("RUC"))
                .andExpect(jsonPath("$.facturacion.numeroDocumentoEnmascarado").value("****0970"))
                .andExpect(jsonPath("$.facturacion.razonSocial").value(RAZON))
                .andExpect(jsonPath("$.facturacion.direccionFiscal").value(DIRECCION))
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("NO_INICIADA"));

        var cuerpo = texto(respuesta);
        assertThat(cuerpo).doesNotContain(RUC).doesNotContain(huella.calcular(RUC)).doesNotContain("$2a$")
                .doesNotContain("hash").doesNotContain("contrasena").doesNotContain("perfiles/")
                .doesNotContain("token");
    }

    @Test
    @DisplayName("HU-20 R5: el USUARIO ve su perfil sin facturación")
    void consultaUsuario() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        consultar(sesion).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.correo").value(CORREO_USUARIO))
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.facturacion").doesNotExist());
    }

    // ── Datos básicos (CA1) ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R8, R47, R7: CA1 guarda nombre y teléfono, el GET inmediato los devuelve y se audita solo "
            + "qué campos cambiaron")
    void ca1GuardaYSeReflejaDeInmediato() throws Exception {
        var id = crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        guardarDatos(sesion, "{\"nombre\":\"  Ana María Secreta \",\"telefono\":\"923456789\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.nombre").value("Ana María Secreta"))
                .andExpect(jsonPath("$.telefono").value("923456789"));
        consultar(sesion).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana María Secreta"))
                .andExpect(jsonPath("$.telefono").value("923456789"));

        var registros = auditorias("PERFIL_ACTUALIZADO");
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(registros.getFirst().get("ip")).isEqualTo(ip);
        assertThat(registros.getFirst().get("entidad")).isEqualTo("usuario");
        assertThat(registros.getFirst().get("detalle")).isEqualTo("campos=nombre,telefono");

        guardarDatos(sesion, "{\"nombre\":\"Ana María Secreta\",\"telefono\":\"923456789\"}")
                .andExpect(status().isOk());
        assertThat(auditorias("PERFIL_ACTUALIZADO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-20 R9: un nombre de 101 caracteres y un teléfono mal formado dan 400 por campo sin guardar")
    void datosInvalidos() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        guardarDatos(sesion, "{\"nombre\":\"" + "a".repeat(101) + "\",\"telefono\":\"812345678\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')].mensaje")
                        .value(RegistroArrendadorDTO.MENSAJE_NOMBRE))
                .andExpect(jsonPath("$.errores[?(@.campo=='telefono')].mensaje")
                        .value(RegistroArrendadorDTO.MENSAJE_TELEFONO));

        assertThat(filaUsuario(id).get("nombre")).isNull();
        assertThat(filaUsuario(id).get("telefono")).isNull();
        assertThat(auditorias("PERFIL_ACTUALIZADO")).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R10: un ARRENDADOR con nombre en blanco y sin teléfono recibe un error en cada campo")
    void arrendadorSinObligatorios() throws Exception {
        var id = crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        guardarDatos(sesion, "{\"nombre\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='telefono')]").exists());

        assertThat(filaUsuario(id).get("nombre")).isEqualTo(NOMBRE_ARRENDADOR);
        assertThat(filaUsuario(id).get("telefono")).isEqualTo(TELEFONO_ARRENDADOR);
    }

    @Test
    @DisplayName("HU-20 R11: un USUARIO que deja nombre y teléfono vacíos los guarda nulos")
    void usuarioVaciaDatos() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        guardarDatos(sesion, "{\"nombre\":\"Carla\",\"telefono\":\"912345678\"}").andExpect(status().isOk());

        guardarDatos(sesion, "{\"nombre\":\"  \",\"telefono\":\"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist());

        assertThat(filaUsuario(id).get("nombre")).isNull();
        assertThat(filaUsuario(id).get("telefono")).isNull();
    }

    @Test
    @DisplayName("HU-20 R12: los datos básicos no piden contraseña y no cambian correo, foto ni facturación")
    void datosSinContrasena() throws Exception {
        var id = crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);
        var perfilAntes = filaPerfil(id);

        guardarDatos(sesion, "{\"nombre\":\"Ana\",\"telefono\":\"912345678\",\"contrasena\":\"Incorrecta1\","
                + "\"razonSocial\":\"Otra SAC\",\"correo\":\"otro@correo.pe\"}").andExpect(status().isOk());

        assertThat(filaUsuario(id).get("correo")).isEqualTo(CORREO_ARRENDADOR);
        assertThat(filaUsuario(id).get("correo_pendiente")).isNull();
        assertThat(filaUsuario(id).get("foto_ruta")).isNull();
        assertThat(filaPerfil(id)).isEqualTo(perfilAntes);
        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
    }

    // ── Foto (CA1, RNF-SEG-08) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R13, R16, R45, R48: un JPG con EXIF se guarda re-codificado y cifrado; el GET lo devuelve sin "
            + "EXIF, con su tipo, nosniff y no-store")
    void subeYVeFoto() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        var jpeg = ArchivosDePrueba.jpegConExif();

        subirFoto(sesion, "yo.jpg", "image/jpeg", jpeg).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tieneFoto").value(true));

        var ruta = rutaFoto(id);
        assertThat(ruta).matches("perfiles/[0-9a-f-]{36}");
        assertThat(filaUsuario(id).get("foto_tipo_mime")).isEqualTo("image/jpeg");
        var servida = verFoto(sesion).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ArchivosDePrueba.contiene(servida, ArchivosDePrueba.SECRETO_EXIF)).isFalse();
        assertThat(servida).startsWith((byte) 0xFF, (byte) 0xD8);

        var enDisco = Files.readAllBytes(directorio.resolve(ruta));
        assertThat(ArchivosDePrueba.contiene(enDisco, ArchivosDePrueba.SECRETO_EXIF)).isFalse();
        assertThat(Arrays.equals(enDisco, servida)).isFalse();
        assertThat(indiceDe(enDisco, Arrays.copyOfRange(servida, 0, 64))).isNegative();
        assertThat(cifradorArchivos.descifrar(enDisco)).isEqualTo(servida);

        var registros = auditorias("FOTO_PERFIL_ACTUALIZADA");
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(registros.getFirst().get("ip")).isEqualTo(ip);
        assertThat(registros.getFirst().get("detalle")).isEqualTo("mime=image/jpeg;tamano=" + jpeg.length);
        assertThat(registros.getFirst().get("detalle").toString()).doesNotContain("perfiles/");
    }

    private static int indiceDe(byte[] contenido, byte[] buscado) {
        for (int i = 0; i + buscado.length <= contenido.length; i++) {
            if (Arrays.equals(contenido, i, i + buscado.length, buscado, 0, buscado.length)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("HU-20 R14: un PDF renombrado .jpg, un texto, una foto vacía o de más de 5 MiB dan 400 en foto sin "
            + "cambiar la vigente")
    void rechazaFotos() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        subirFoto(sesion, "yo.png", "image/png", ArchivosDePrueba.png(30, 30)).andExpect(status().isOk());
        var vigente = rutaFoto(id);
        var archivosAntes = archivosEnPerfiles();

        subirFoto(sesion, "foto.jpg", "image/jpeg", ArchivosDePrueba.pdf(2048)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("foto"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(PerfilService.MENSAJE_TIPO_FOTO));
        subirFoto(sesion, "foto.png", "image/png", ArchivosDePrueba.texto()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(PerfilService.MENSAJE_TIPO_FOTO));
        subirFoto(sesion, "foto.png", "image/png", ArchivosDePrueba.pngIlegible()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("foto"));
        subirFoto(sesion, "vacia.png", "image/png", new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(PerfilService.MENSAJE_FALTA_FOTO));
        var grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(ArchivosDePrueba.png(10, 10), 0, grande, 0, 8);
        subirFoto(sesion, "grande.png", "image/png", grande).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("foto"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El archivo debe pesar como máximo 5 MB."));
        mvc.perform(con(sesion, multipart(HttpMethod.PUT, FOTO))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("foto"));

        assertThat(rutaFoto(id)).isEqualTo(vigente);
        assertThat(archivosEnPerfiles()).isEqualTo(archivosAntes);
        assertThat(auditorias("FOTO_PERFIL_ACTUALIZADA")).hasSize(1);
    }

    private long archivosEnPerfiles() throws Exception {
        var carpeta = directorio.resolve("perfiles");
        if (!Files.exists(carpeta)) {
            return 0;
        }
        try (var archivos = Files.list(carpeta)) {
            return archivos.count();
        }
    }

    @Test
    @DisplayName("HU-20 R15, R17, R48: reemplazar borra el archivo anterior y quitar deja la cuenta sin foto y sin "
            + "archivo")
    void reemplazaYQuita() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        subirFoto(sesion, "a.png", "image/png", ArchivosDePrueba.png(30, 30)).andExpect(status().isOk());
        var primera = directorio.resolve(rutaFoto(id));
        assertThat(Files.exists(primera)).isTrue();

        subirFoto(sesion, "b.png", "image/png", ArchivosDePrueba.png(40, 40)).andExpect(status().isOk());
        var segunda = directorio.resolve(rutaFoto(id));

        assertThat(segunda).isNotEqualTo(primera);
        assertThat(Files.exists(primera)).isFalse();
        assertThat(Files.exists(segunda)).isTrue();
        assertThat(filaUsuario(id).get("foto_tipo_mime")).isEqualTo("image/png");

        quitarFoto(sesion).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tieneFoto").value(false));

        assertThat(Files.exists(segunda)).isFalse();
        assertThat(filaUsuario(id).get("foto_ruta")).isNull();
        assertThat(filaUsuario(id).get("foto_tipo_mime")).isNull();
        verFoto(sesion).andExpect(status().isNotFound());
        var eliminadas = auditorias("FOTO_PERFIL_ELIMINADA");
        assertThat(eliminadas).hasSize(1);
        assertThat(eliminadas.getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(eliminadas.getFirst().get("ip")).isEqualTo(ip);
    }

    @Test
    @DisplayName("HU-20 R17: quitar sin foto previa responde 200 sin foto y no audita")
    void quitarSinFoto() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        quitarFoto(sesion).andExpect(status().isOk()).andExpect(jsonPath("$.tieneFoto").value(false));

        assertThat(auditorias("FOTO_PERFIL_ELIMINADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R16: otra cuenta no ve la foto del titular: su GET responde 404 y nunca los bytes ajenos")
    void otroNoVeLaFoto() throws Exception {
        crearUsuario(CORREO_USUARIO);
        crearUsuario(CORREO_OTRO);
        var titular = token(CORREO_USUARIO);
        var otro = token(CORREO_OTRO);
        subirFoto(titular, "a.png", "image/png", ArchivosDePrueba.png(30, 30)).andExpect(status().isOk());

        verFoto(otro).andExpect(status().isNotFound());
        verFoto(titular).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    }

    // ── Confirmación pública (R34) ───────────────────────────────────────

    @Test
    @DisplayName("HU-20 R34, R7: la confirmación se atiende sin sesión (400, nunca 401) y con no-store")
    void confirmacionPublica() throws Exception {
        mvc.perform(desde(post(CONFIRMACIONES), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"no-es-un-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.mensaje").value(CambioCorreoService.MENSAJE_ENLACE_INVALIDO));
    }

    @Test
    @DisplayName("HU-20 R34: un GET o un token en la URL no confirman el cambio de correo")
    void tokenEnLaUrlNoConfirma() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        mvc.perform(con(sesion, post(CAMBIOS_CORREO)).contentType(MediaType.APPLICATION_JSON).content(CUERPO_CORREO))
                .andExpect(status().isAccepted());
        var enlace = notificador.cambios().getFirst().enlace();
        var token = enlace.substring(enlace.indexOf("#token=") + "#token=".length());

        mvc.perform(desde(get(CONFIRMACIONES + "?token=" + token), ip)).andExpect(status().isUnauthorized());
        mvc.perform(con(sesion, get(CONFIRMACIONES + "?token=" + token))).andExpect(status().isMethodNotAllowed());
        mvc.perform(desde(post(CONFIRMACIONES + "?token=" + token), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(desde(post(CONFIRMACIONES + "?token=" + token), ip)).andExpect(status().isBadRequest());

        assertThat(filaUsuario(id).get("correo")).isEqualTo(CORREO_USUARIO);
        assertThat(filaUsuario(id).get("correo_pendiente")).isEqualTo("nuevo.perfil@correo.pe");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token WHERE usado_en IS NOT NULL", Integer.class))
                .isZero();
    }
}
