package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;

class FotoPublicaControllerIT extends EspaciosControllerSoporte {

    private record Fotos(String token, long espacioId, String jpeg, String png) {
    }

    private static String uuidDe(Map<String, Object> fila) {
        var ruta = (String) fila.get("ruta_archivo");
        return ruta.substring(ruta.indexOf('/') + 1);
    }

    /** Espacio de Ana con una foto JPEG y una PNG, en el estado pedido. */
    private Fotos espacioConFotos(String estado) throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subir(token, id, foto("a.jpg", "image/jpeg", ArchivosDePrueba.jpeg(40, 30))).andExpect(status().isCreated());
        subir(token, id, foto("b.png", "image/png", ArchivosDePrueba.png(30, 20))).andExpect(status().isCreated());
        forzarEstado(id, estado);
        var filas = fotosEnBd(id);
        return new Fotos(token, id, uuidDe(filas.get(0)), uuidDe(filas.get(1)));
    }

    private byte[] enDisco(String uuid) throws Exception {
        return Files.readAllBytes(directorio.resolve("espacios").resolve(uuid));
    }

    private static void noEncontrada(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isNotFound())
                .andExpect(content().json("{\"mensaje\":\"" + NO_ENCONTRADO + "\",\"errores\":[]}", true))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("HU-07 R39, R43: la foto de un PUBLICADO se sirve sin sesión, con otra cuenta y al dueño, cacheable")
    void fotoPublicada() throws Exception {
        var fotos = espacioConFotos("PUBLICADO");
        crearArrendador(OTRO_CORREO);
        var otro = token(OTRO_CORREO);

        for (var token : new String[] {null, otro, fotos.token()}) {
            for (var caso : List.of(Map.entry(fotos.jpeg(), "image/jpeg"), Map.entry(fotos.png(), "image/png"))) {
                var cuerpo = verFoto(token, caso.getKey()).andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, caso.getValue()))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"))
                        .andReturn().getResponse().getContentAsByteArray();
                assertThat(cuerpo).isEqualTo(enDisco(caso.getKey()));
            }
        }
    }

    @Test
    @DisplayName("HU-09 R22, R24: con el arrendador SUSPENDIDA la foto de su PUBLICADO responde 404 sin sesión; al "
            + "volver a ACTIVA se sirve otra vez pública")
    void fotoPublicadaSegunElEstadoDelArrendador() throws Exception {
        var fotos = espacioConFotos("PUBLICADO");
        crearArrendador(OTRO_CORREO);
        var otro = token(OTRO_CORREO);

        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE correo = ?", CORREO);
        for (var token : new String[] {null, otro}) {
            noEncontrada(verFoto(token, fotos.jpeg()));
            noEncontrada(verFoto(token, fotos.png()));
        }

        jdbc.update("UPDATE usuario SET estado = 'ACTIVA' WHERE correo = ?", CORREO);
        var cuerpo = verFoto(null, fotos.jpeg()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(cuerpo).isEqualTo(enDisco(fotos.jpeg()));
        assertThat(jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, fotos.espacioId()))
                .isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("HU-07 R54: la foto de un BORRADOR o un PAUSADO se sirve a su dueño con no-store")
    void fotoNoPublicadaDelDueno() throws Exception {
        var fotos = espacioConFotos("BORRADOR");

        for (var estado : List.of("BORRADOR", "PAUSADO")) {
            forzarEstado(fotos.espacioId(), estado);
            var cuerpo = verFoto(fotos.token(), fotos.png()).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(cuerpo).isEqualTo(enDisco(fotos.png()));
        }
    }

    @Test
    @DisplayName("HU-07 R55: la foto de un BORRADOR sin sesión, con token inválido o de otra cuenta responde 404")
    void fotoNoPublicadaAjena() throws Exception {
        var fotos = espacioConFotos("BORRADOR");
        crearArrendador(OTRO_CORREO);
        var otro = token(OTRO_CORREO);

        for (var token : new String[] {null, "token-que-no-existe", otro}) {
            noEncontrada(verFoto(token, fotos.jpeg()));
        }
    }

    @Test
    @DisplayName("HU-07 R55: un uuid inexistente, mal formado, en mayúsculas o con .. responde el mismo 404")
    void uuidInvalido() throws Exception {
        var fotos = espacioConFotos("PUBLICADO");
        var respuestas = new ArrayList<MvcResult>();

        for (var uuid : new String[] {"9f8fad5b-d9cb-469f-a165-70867728950e", "no-es-uuid",
                fotos.jpeg().toUpperCase(), fotos.jpeg().replace("-", ".."), "a..b"}) {
            var respuesta = verFoto(null, uuid);
            noEncontrada(respuesta);
            respuestas.add(respuesta.andReturn());
        }
        assertThat(respuestas).extracting(r -> r.getResponse().getContentAsString())
                .containsOnly(respuestas.get(0).getResponse().getContentAsString());
    }

    @Test
    @DisplayName("HU-07 R55, R35: una foto eliminada o cuyo archivo falta en disco responde 404")
    void fotoEliminadaOSinArchivo() throws Exception {
        var fotos = espacioConFotos("PUBLICADO");
        var jpegId = ((Number) fotosEnBd(fotos.espacioId()).get(0).get("id")).longValue();

        quitar(fotos.token(), fotos.espacioId(), jpegId).andExpect(status().isOk());
        noEncontrada(verFoto(null, fotos.jpeg()));

        Files.delete(directorio.resolve("espacios").resolve(fotos.png()));
        assertThat(fotosEnBd(fotos.espacioId())).hasSize(1);
        noEncontrada(verFoto(fotos.token(), fotos.png()));
    }
}
