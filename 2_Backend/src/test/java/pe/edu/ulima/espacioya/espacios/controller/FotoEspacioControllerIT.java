package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;

class FotoEspacioControllerIT extends EspaciosControllerSoporte {

    private static final String ESTADO_NO_EDITABLE = "Este espacio ya no se puede editar.";
    private static final String MAXIMO_FOTOS = "Ya tienes 5 fotos. Quita una para subir otra.";
    private static final String FALTA_FOTO = "Elige una foto.";
    private static final String TIPO_FOTO = "La foto debe ser JPG o PNG.";
    private static final String TAMANO_EXCEDIDO = "El archivo debe pesar como máximo 5 MB.";
    private static final String IMAGEN_ILEGIBLE =
            "No pudimos leer la imagen. Revisa que no esté dañada y vuelve a subirla.";
    private static final String NOMBRE_ORIGINAL = "mi-cancha-secreta-ana.jpg";
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private static MockMultipartFile jpeg(int ancho) {
        return foto(NOMBRE_ORIGINAL, "image/jpeg", ArchivosDePrueba.jpeg(ancho, 30));
    }

    private ResultActions subirJpeg(String token, long id, int ancho) throws Exception {
        return subir(token, id, jpeg(ancho));
    }

    private static String uuidDe(Map<String, Object> fila) {
        var ruta = (String) fila.get("ruta_archivo");
        return ruta.substring(ruta.indexOf('/') + 1);
    }

    private byte[] archivo(Map<String, Object> fila) throws Exception {
        return Files.readAllBytes(directorio.resolve((String) fila.get("ruta_archivo")));
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        return contenido.length >= firma.length && Arrays.equals(contenido, 0, firma.length, firma, 0, firma.length);
    }

    private void campoFoto(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("foto"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    // ── Agregar (CA1 "hasta 5 fotos") ────────────────────────────────────

    @Test
    @DisplayName("HU-07 R25, R26: cada foto responde 201 con orden n+1 y su fila con el tipo MIME real")
    void agregaFotos() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var png = foto("se-llama-jpg.jpg", "image/jpeg", ArchivosDePrueba.png(50, 40));

        subirJpeg(token, id, 40).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fotos.length()").value(1))
                .andExpect(jsonPath("$.fotos[0].orden").value(1));
        subir(token, id, png).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fotos.length()").value(2))
                .andExpect(jsonPath("$.fotos[1].orden").value(2))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        var filas = fotosEnBd(id);
        assertThat(filas).extracting(f -> ((Number) f.get("orden")).intValue()).containsExactly(1, 2);
        assertThat(filas).extracting(f -> f.get("tipo_mime")).containsExactly("image/jpeg", "image/png");
        assertThat(filas).extracting(f -> (String) f.get("ruta_archivo"))
                .allSatisfy(ruta -> assertThat(ruta).matches("^espacios/" + UUID + "$"));
    }

    @Test
    @DisplayName("HU-07 R10, R11: las fotos van por orden con url /api/fotos/<UUID> estable, sin firma ni expiración")
    void urlEstable() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subirJpeg(token, id, 40);
        subirJpeg(token, id, 60);
        var filas = fotosEnBd(id);

        var primero = consultar(token, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var i = 0; i < 3; i++) {
            reloj.avanzar(Duration.ofMinutes(20));
            consultar(token, id).andExpect(status().isOk());
        }
        var despues = consultar(token, id).andReturn().getResponse().getContentAsString();

        var fotos = fotosDe(primero);
        assertThat(fotos).allSatisfy(f -> assertThat(f).containsOnlyKeys("id", "orden", "url"));
        assertThat(fotos).extracting(f -> f.get("url"))
                .containsExactly(FOTOS_PUBLICAS + uuidDe(filas.get(0)), FOTOS_PUBLICAS + uuidDe(filas.get(1)));
        assertThat(fotos).extracting(f -> (String) f.get("url")).noneMatch(url -> url.contains("?"));
        assertThat(fotosDe(despues)).isEqualTo(fotos);
    }

    @Test
    @DisplayName("HU-07 R27, R32, R50: la sexta foto responde máximo de fotos sin archivo nuevo y se audita")
    void sextaFoto() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        for (var i = 0; i < 5; i++) {
            subirJpeg(token, id, 20 + i).andExpect(status().isCreated());
        }
        var archivos = archivosAlmacenados();

        subirJpeg(token, id, 50).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MAXIMO_FOTOS));

        assertThat(fotosEnBd(id)).hasSize(5);
        assertThat(archivosAlmacenados()).containsExactlyInAnyOrderElementsOf(archivos);
        var rechazos = auditorias("FOTO_ESPACIO_RECHAZADA");
        assertThat(rechazos).hasSize(1);
        assertThat(rechazos.get(0)).containsEntry("detalle", "fotos=MAXIMO").containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip);
    }

    @Test
    @DisplayName("HU-07 R28, R32: sin parte foto o con 0 bytes responde 400 en foto sin cambios")
    void faltaFoto() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        campoFoto(subir(token, id), FALTA_FOTO);
        campoFoto(subir(token, id, foto("vacia.jpg", "image/jpeg", new byte[0])), FALTA_FOTO);

        assertThat(fotosEnBd(id)).isEmpty();
        assertThat(archivosAlmacenados()).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R29, R32: una foto de 5 242 881 bytes responde 400 tamaño excedido en foto sin cambios")
    void fotoDemasiadoGrande() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var grande = new byte[5_242_881];
        System.arraycopy(ArchivosDePrueba.jpeg(10, 10), 0, grande, 0, 100);

        campoFoto(subir(token, id, foto("grande.jpg", "image/jpeg", grande)), TAMANO_EXCEDIDO);

        assertThat(fotosEnBd(id)).isEmpty();
        assertThat(archivosAlmacenados()).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R30, R32, R50: un PDF o un texto que dice ser JPG responde tipo de foto sin cambios")
    void tipoDeFoto() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        campoFoto(subir(token, id, foto("foto.pdf", "application/pdf", ArchivosDePrueba.pdf(2048))), TIPO_FOTO);
        campoFoto(subir(token, id, foto("x.jpg", "image/jpeg", ArchivosDePrueba.texto())), TIPO_FOTO);

        assertThat(fotosEnBd(id)).isEmpty();
        assertThat(archivosAlmacenados()).isEmpty();
        assertThat(auditorias("FOTO_ESPACIO_RECHAZADA")).extracting(a -> a.get("detalle"))
                .containsExactly("foto=TIPO_NO_PERMITIDO", "foto=TIPO_NO_PERMITIDO");
    }

    @Test
    @DisplayName("HU-07 R31, R32: una firma PNG con contenido que no es imagen responde imagen ilegible sin cambios")
    void imagenIlegible() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        campoFoto(subir(token, id, foto("rota.png", "image/png", ArchivosDePrueba.pngIlegible())), IMAGEN_ILEGIBLE);

        assertThat(fotosEnBd(id)).isEmpty();
        assertThat(archivosAlmacenados()).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R33: el archivo guardado no conserva el EXIF de un JPEG ni el tEXt de un PNG")
    void sinMetadatos() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var conExif = ArchivosDePrueba.jpegConExif();
        var conTexto = ArchivosDePrueba.pngConTexto();
        assertThat(ArchivosDePrueba.contiene(conExif, ArchivosDePrueba.SECRETO_EXIF)).isTrue();
        assertThat(ArchivosDePrueba.contiene(conTexto, ArchivosDePrueba.SECRETO_PNG)).isTrue();

        subir(token, id, foto("gps.jpg", "image/jpeg", conExif)).andExpect(status().isCreated());
        subir(token, id, foto("texto.png", "image/png", conTexto)).andExpect(status().isCreated());

        var filas = fotosEnBd(id);
        var jpeg = archivo(filas.get(0));
        var png = archivo(filas.get(1));
        assertThat(ArchivosDePrueba.contiene(jpeg, ArchivosDePrueba.SECRETO_EXIF)).isFalse();
        assertThat(ArchivosDePrueba.contiene(jpeg, "Exif")).isFalse();
        assertThat(ArchivosDePrueba.contiene(png, ArchivosDePrueba.SECRETO_PNG)).isFalse();
        assertThat(ArchivosDePrueba.contiene(png, "tEXt")).isFalse();
    }

    @Test
    @DisplayName("HU-07 R37: la foto queda sin cifrar en <directorio>/espacios/<UUID> y no en la base de datos")
    void guardadaSinCifrarFueraDeLaBd() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subirJpeg(token, id, 40).andExpect(status().isCreated());
        subir(token, id, foto("b.png", "image/png", ArchivosDePrueba.png(30, 30))).andExpect(status().isCreated());

        var filas = fotosEnBd(id);
        var jpeg = archivo(filas.get(0));
        var png = archivo(filas.get(1));
        assertThat(directorio.resolve("espacios").resolve(uuidDe(filas.get(0)))).isRegularFile();
        assertThat(empiezaCon(jpeg, FIRMA_JPEG)).isTrue();
        assertThat(empiezaCon(png, FIRMA_PNG)).isTrue();
        var servida = verFoto(token, uuidDe(filas.get(0))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        assertThat(servida).isEqualTo(jpeg);
        var binarias = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = "
                + "'foto_espacio' AND data_type IN ('bytea', 'oid')", Integer.class);
        assertThat(binarias).isZero();
    }

    @Test
    @DisplayName("HU-07 R38: la base de datos rechaza un orden fuera de 1 a 5 y un orden repetido")
    void restriccionesDeOrden() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subirJpeg(token, id, 40).andExpect(status().isCreated());
        var insertar = "INSERT INTO foto_espacio (espacio_id, ruta_archivo, tipo_mime, orden) VALUES (?, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insertar, id, "espacios/1f8fad5b-d9cb-469f-a165-70867728950e",
                "image/jpeg", 6)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "espacios/2f8fad5b-d9cb-469f-a165-70867728950e",
                "image/jpeg", 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "espacios/3f8fad5b-d9cb-469f-a165-70867728950e",
                "image/jpeg", 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(fotosEnBd(id)).hasSize(1);
    }

    // ── Quitar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R34, R35, R49: quitar la foto 2 de 3 renumera sin huecos y borra su fila y su archivo")
    void quitaFoto() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        for (var ancho : new int[] {40, 50, 60}) {
            subirJpeg(token, id, ancho).andExpect(status().isCreated());
        }
        var filas = fotosEnBd(id);
        var segunda = ((Number) filas.get(1).get("id")).longValue();
        var archivoSegunda = directorio.resolve((String) filas.get(1).get("ruta_archivo"));
        assertThat(archivoSegunda).isRegularFile();

        quitar(token, id, segunda).andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos.length()").value(2))
                .andExpect(jsonPath("$.fotos[0].orden").value(1))
                .andExpect(jsonPath("$.fotos[1].orden").value(2))
                .andExpect(jsonPath("$.fotos[0].id").value(filas.get(0).get("id")))
                .andExpect(jsonPath("$.fotos[1].id").value(filas.get(2).get("id")));

        var restantes = fotosEnBd(id);
        assertThat(restantes).extracting(f -> f.get("id")).containsExactly(filas.get(0).get("id"),
                filas.get(2).get("id"));
        assertThat(restantes).extracting(f -> ((Number) f.get("orden")).intValue()).containsExactly(1, 2);
        assertThat(archivoSegunda).doesNotExist();
        assertThat(archivosAlmacenados()).hasSize(2);
        var auditoria = auditorias("FOTO_ESPACIO_ELIMINADA");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "foto=" + segunda + ";orden=2");
        assertThat(instante(auditoria.get(0).get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-07 R36: una foto de otro espacio propio responde 404 y no se borra ninguna")
    void fotoDeOtroEspacio() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var uno = crearEspacio(token);
        var otro = crearEspacio(token);
        subirJpeg(token, uno, 40).andExpect(status().isCreated());
        subirJpeg(token, otro, 50).andExpect(status().isCreated());
        var fotoDelOtro = ((Number) fotosEnBd(otro).get(0).get("id")).longValue();

        quitar(token, uno, fotoDelOtro).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));

        assertThat(fotosEnBd(uno)).hasSize(1);
        assertThat(fotosEnBd(otro)).hasSize(1);
        assertThat(archivosAlmacenados()).hasSize(2);
    }

    // ── Estado, propiedad y auditoría ────────────────────────────────────

    @Test
    @DisplayName("HU-07 R23, R32: en BAJA u OCULTO subir o quitar fotos responde estado no editable sin cambios")
    void estadoNoEditable() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        for (var estado : List.of("BAJA", "OCULTO")) {
            var id = crearEspacio(token);
            subirJpeg(token, id, 40).andExpect(status().isCreated());
            var fotoId = ((Number) fotosEnBd(id).get(0).get("id")).longValue();
            forzarEstado(id, estado);
            var archivos = archivosAlmacenados();

            for (var respuesta : List.of(subirJpeg(token, id, 50), quitar(token, id, fotoId))) {
                respuesta.andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.mensaje").value(ESTADO_NO_EDITABLE));
            }

            assertThat(fotosEnBd(id)).hasSize(1);
            assertThat(archivosAlmacenados()).containsExactlyInAnyOrderElementsOf(archivos);
        }
    }

    @Test
    @DisplayName("HU-07 R40: subir o quitar fotos de un espacio ajeno o inexistente responde 404 sin cambios")
    void ajenoOInexistente() throws Exception {
        crearArrendador(CORREO);
        crearArrendador(OTRO_CORREO);
        var deAna = token(CORREO);
        var id = crearEspacio(deAna);
        subirJpeg(deAna, id, 40).andExpect(status().isCreated());
        var fotoId = ((Number) fotosEnBd(id).get(0).get("id")).longValue();
        var beto = token(OTRO_CORREO);
        var archivos = archivosAlmacenados();

        for (var espacioId : List.of(id, 999_999L)) {
            for (var respuesta : List.of(subirJpeg(beto, espacioId, 50), quitar(beto, espacioId, fotoId))) {
                respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            }
        }
        assertThat(fotosEnBd(id)).hasSize(1);
        assertThat(archivosAlmacenados()).containsExactlyInAnyOrderElementsOf(archivos);
        assertThat(auditorias("FOTO_ESPACIO_RECHAZADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R49: agregar audita FOTO_ESPACIO_AGREGADA con foto, orden, mime y tamaño recibido")
    void auditaAgregada() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var png = ArchivosDePrueba.pngConTexto();

        subir(token, id, foto("cancha.png", "image/png", png)).andExpect(status().isCreated());

        var fotoId = fotosEnBd(id).get(0).get("id");
        var auditoria = auditorias("FOTO_ESPACIO_AGREGADA");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "foto=" + fotoId + ";orden=1;mime=image/png;tamano=" + png.length);
        assertThat(instante(auditoria.get(0).get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-07 R51: ninguna auditoría guarda el nombre original de la foto ni su clave de almacenamiento")
    void auditoriaSinNombreNiClave() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subirJpeg(token, id, 40).andExpect(status().isCreated());
        subir(token, id, foto(NOMBRE_ORIGINAL, "image/jpeg", ArchivosDePrueba.texto()))
                .andExpect(status().isBadRequest());
        quitar(token, id, ((Number) fotosEnBd(id).get(0).get("id")).longValue()).andExpect(status().isOk());

        var detalles = todasLasAuditorias().stream().map(a -> String.valueOf(a.get("detalle"))).toList();
        assertThat(detalles).hasSizeGreaterThanOrEqualTo(4);
        assertThat(detalles).noneMatch(d -> d.contains(NOMBRE_ORIGINAL) || d.contains("espacios/")
                || d.contains("mi-cancha"));
    }
}
