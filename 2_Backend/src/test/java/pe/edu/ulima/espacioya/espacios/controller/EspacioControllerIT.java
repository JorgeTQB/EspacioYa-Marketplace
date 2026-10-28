package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;

class EspacioControllerIT extends EspaciosControllerSoporte {

    private static final String IDENTIDAD_NO_VERIFICADA =
            "Para publicar tus espacios, primero debes verificar tu identidad.";
    private static final String SOLO_BORRADOR = "Solo puedes publicar un espacio que está en borrador.";
    private static final String SIN_FOTOS = "Agrega al menos una foto antes de publicar.";
    private static final String ESTADO_NO_EDITABLE = "Este espacio ya no se puede editar.";
    private static final List<String> CLAVES_DETALLE = List.of("id", "nombre", "tipo", "direccion", "zona",
            "zonaNombre", "capacidad", "precioHora", "descripcion", "estado", "creadoEn", "actualizadoEn", "fotos");

    private void subirUnaFoto(String token, long id) throws Exception {
        subir(token, id, foto("cancha.jpg", "image/jpeg", ArchivosDePrueba.jpeg(40, 30)))
                .andExpect(status().isCreated());
    }

    private static void error(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores.length()").value(0));
    }

    // ── Creación (CA1) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R1, R2: crear responde 201 BORRADOR sin fotos y guarda la fila recortada con fechas del reloj")
    void crearBorrador() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var conEspacios = ficha("nombre", "  Cancha Los Olivos  ");
        conEspacios.put("direccion", " Av. Universitaria 1234 ");
        conEspacios.put("descripcion", "  " + DESCRIPCION + "  ");

        crear(token, conEspacios)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.fotos.length()").value(0))
                .andExpect(jsonPath("$.nombre").value("Cancha Los Olivos"))
                .andExpect(jsonPath("$.zonaNombre").value("Los Olivos"));

        var filas = espacios();
        assertThat(filas).hasSize(1);
        var fila = filas.get(0);
        assertThat(fila.get("arrendador_id")).isEqualTo(ana.getId());
        assertThat(fila.get("nombre")).isEqualTo("Cancha Los Olivos");
        assertThat(fila.get("direccion")).isEqualTo("Av. Universitaria 1234");
        assertThat(fila.get("descripcion")).isEqualTo(DESCRIPCION);
        assertThat(fila.get("tipo")).isEqualTo("CANCHA");
        assertThat(fila.get("zona")).isEqualTo("LOS_OLIVOS");
        assertThat(fila.get("capacidad")).isEqualTo(20);
        assertThat(fila.get("estado")).isEqualTo("BORRADOR");
        assertThat(instante(fila.get("creado_en"))).isEqualTo(reloj.instant());
        assertThat(instante(fila.get("actualizado_en"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-07 R3: id, arrendadorId, usuarioId y estado del cuerpo se ignoran al crear y al editar")
    void ignoraIdentidadYEstadoDelCuerpo() throws Exception {
        var ana = crearArrendador(CORREO);
        var beto = crearArrendador(OTRO_CORREO);
        var token = token(CORREO);
        var cuerpo = ficha("estado", "PUBLICADO");
        cuerpo.put("arrendadorId", beto.getId());
        cuerpo.put("usuarioId", beto.getId());
        cuerpo.put("id", 999_999);

        var id = ((Number) mapa(crear(token, cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andReturn().getResponse().getContentAsString()).get("id")).longValue();
        assertThat(id).isNotEqualTo(999_999L);
        assertThat(espacio(id).get("arrendador_id")).isEqualTo(ana.getId());

        cuerpo.put("nombre", "Cancha renombrada");
        editar(token, id, cuerpo).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
        assertThat(espacio(id).get("arrendador_id")).isEqualTo(ana.getId());
        assertThat(espacio(id).get("estado")).isEqualTo("BORRADOR");
        assertThat(espacios()).hasSize(1);
    }

    @Test
    @DisplayName("HU-07 R4, R6: una ficha con tres campos inválidos responde un error por campo y no guarda nada")
    void fichaInvalida() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var cuerpo = ficha("nombre", "ab");
        cuerpo.put("capacidad", 0);
        cuerpo.put("precioHora", 80.555);

        var respuesta = crear(token, cuerpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        var errores = (List<Map<String, Object>>) mapa(respuesta).get("errores");
        assertThat(errores).extracting(e -> e.get("campo"))
                .containsExactlyInAnyOrder("nombre", "capacidad", "precioHora");
        assertThat(errores).extracting(e -> e.get("mensaje")).contains("Escribe un nombre de 3 a 100 caracteres.");
        assertThat(espacios()).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R4, R6: una capacidad decimal o en texto responde 400 en capacidad y no se trunca")
    void capacidadNoEntera() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);

        for (Object capacidad : List.<Object>of(20.7, 0.5, "20", "20.7", true)) {
            crear(token, ficha("capacidad", capacidad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("capacidad"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("La capacidad debe ser un número entero entre 1 y 100 000."));
        }
        assertThat(espacios()).isEmpty();

        var id = crearEspacio(token);
        var antes = espacio(id);
        editar(token, id, ficha("capacidad", 30.2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("capacidad"));
        assertThat(espacio(id)).isEqualTo(antes);
    }

    @Test
    @DisplayName("HU-07 R4: una ficha sin descripción o con descripción en blanco responde 400 en descripcion")
    void sinDescripcion() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var sinDescripcion = ficha();
        sinDescripcion.remove("descripcion");

        for (var cuerpo : List.of(sinDescripcion, ficha("descripcion", "         "))) {
            crear(token, cuerpo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("descripcion"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("Escribe una descripción de 10 a 2000 caracteres."));
        }
        assertThat(espacios()).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R5, R6: una zona fuera del catálogo responde 400 en zona al crear y al editar sin cambios")
    void zonaInexistente() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);

        crear(token, ficha("zona", "NARNIA")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("zona"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Elige una zona de la lista."));
        assertThat(espacios()).isEmpty();

        var id = crearEspacio(token);
        var antes = espacio(id);
        reloj.avanzar(Duration.ofMinutes(5));
        editar(token, id, ficha("zona", "NARNIA")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("zona"));
        editar(token, id, ficha("capacidad", -1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("capacidad"));
        assertThat(espacio(id)).isEqualTo(antes);
    }

    @Test
    @DisplayName("HU-07 R7, R10: el detalle trae solo los campos del EspacioDTO, sin arrendador, correo ni claves")
    void minimizacion() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var creado = crear(token, ficha()).andReturn().getResponse().getContentAsString();
        var id = ((Number) mapa(creado).get("id")).longValue();
        subirUnaFoto(token, id);

        var detalle = consultar(token, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var listado = listar(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(mapa(creado)).containsOnlyKeys(CLAVES_DETALLE);
        assertThat(mapa(detalle)).containsOnlyKeys(CLAVES_DETALLE);
        assertThat(lista(listado).get(0)).containsOnlyKeys("id", "nombre", "tipo", "zona", "zonaNombre",
                "precioHora", "estado");
        for (var cuerpo : List.of(creado, detalle, listado)) {
            assertThat(cuerpo).doesNotContain("arrendador").doesNotContain(CORREO).doesNotContain("987654321")
                    .doesNotContain("espacios/");
        }
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R9: cada arrendador ve solo sus espacios, del más reciente al más antiguo")
    void listaSoloLosPropios() throws Exception {
        crearArrendador(CORREO);
        crearArrendador(OTRO_CORREO);
        var ana = token(CORREO);
        var beto = token(OTRO_CORREO);
        var primero = crearEspacio(ana);
        reloj.avanzar(Duration.ofMinutes(1));
        crear(beto, ficha("nombre", "Estadio de Beto")).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(1));
        var segundo = ((Number) mapa(crear(ana, ficha("nombre", "Auditorio de Ana")).andReturn().getResponse()
                .getContentAsString()).get("id")).longValue();

        var deAna = lista(listar(ana).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var deBeto = lista(listar(beto).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(deAna).extracting(e -> ((Number) e.get("id")).longValue()).containsExactly(segundo, primero);
        assertThat(deAna.get(1).get("zonaNombre")).isEqualTo("Los Olivos");
        assertThat(deAna.get(1).get("estado")).isEqualTo("BORRADOR");
        assertThat(deBeto).extracting(e -> e.get("nombre")).containsExactly("Estadio de Beto");
    }

    // ── Publicación (CA2, CA3) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R12, R13, R48: con identidad verificada y una foto se publica y se audita")
    void publica() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subirUnaFoto(token, id);
        forzarIdentidad(ana, "VERIFICADA");
        reloj.avanzar(Duration.ofMinutes(5));

        publicar(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.fotos.length()").value(1));

        assertThat(espacio(id).get("estado")).isEqualTo("PUBLICADO");
        assertThat(instante(espacio(id).get("actualizado_en"))).isEqualTo(reloj.instant());
        var auditoria = auditorias("PUBLICACION_ESPACIO");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "estado=BORRADOR->PUBLICADO");
        assertThat(instante(auditoria.get(0).get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-07 R14, R18: publicar un PUBLICADO o un PAUSADO responde solo borrador sin cambios")
    void soloBorrador() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarIdentidad(ana, "VERIFICADA");
        var token = token(CORREO);
        for (var estado : List.of("PUBLICADO", "PAUSADO")) {
            var id = crearEspacio(token);
            subirUnaFoto(token, id);
            forzarEstado(id, estado);
            var antes = espacio(id);
            reloj.avanzar(Duration.ofMinutes(1));

            error(publicar(token, id), SOLO_BORRADOR);

            assertThat(espacio(id)).isEqualTo(antes);
        }
        assertThat(auditorias("PUBLICACION_ESPACIO")).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R15, R18: sin identidad verificada se responde identidad no verificada y sigue BORRADOR")
    void identidadNoVerificada() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        subirUnaFoto(token, id);
        for (var estado : List.of("NO_INICIADA", "EN_REVISION", "RECHAZADA")) {
            forzarIdentidad(ana, estado);
            var antes = espacio(id);
            reloj.avanzar(Duration.ofMinutes(1));

            error(publicar(token, id), IDENTIDAD_NO_VERIFICADA);

            assertThat(espacio(id)).isEqualTo(antes);
        }
        assertThat(espacio(id).get("estado")).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("HU-07 R17, R18: con identidad verificada y sin fotos se responde sin fotos y sigue BORRADOR")
    void sinFotos() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarIdentidad(ana, "VERIFICADA");
        var token = token(CORREO);
        var id = crearEspacio(token);
        var antes = espacio(id);
        reloj.avanzar(Duration.ofMinutes(1));

        error(publicar(token, id), SIN_FOTOS);

        assertThat(espacio(id)).isEqualTo(antes);
    }

    @Test
    @DisplayName("HU-07 R16: sin identidad ni fotos gana identidad no verificada")
    void ordenDeMotivos() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        error(publicar(token, id), IDENTIDAD_NO_VERIFICADA);
    }

    @Test
    @DisplayName("HU-07 R19: crear, editar y subir fotos con identidad verificada no publica el espacio")
    void soloPublicarPublica() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarIdentidad(ana, "VERIFICADA");
        var token = token(CORREO);
        var id = crearEspacio(token);
        editar(token, id, ficha("nombre", "Otro nombre")).andExpect(jsonPath("$.estado").value("BORRADOR"));
        subirUnaFoto(token, id);

        assertThat(espacio(id).get("estado")).isEqualTo("BORRADOR");
        consultar(token, id).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    // ── Edición (CA4) ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R20, R21: editar responde 200 con los nuevos valores y actualiza la misma fila")
    void editar() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var antes = espacio(id);
        reloj.avanzar(Duration.ofMinutes(10));
        var cuerpo = ficha("nombre", " Cancha Norte ");
        cuerpo.put("tipo", "ESTADIO");
        cuerpo.put("zona", "COMAS");

        editar(token, id, cuerpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cancha Norte"))
                .andExpect(jsonPath("$.tipo").value("ESTADIO"))
                .andExpect(jsonPath("$.zona").value("COMAS"))
                .andExpect(jsonPath("$.zonaNombre").value("Comas"));

        var fila = espacio(id);
        assertThat(fila.get("nombre")).isEqualTo("Cancha Norte");
        assertThat(fila.get("estado")).isEqualTo("BORRADOR");
        assertThat(fila.get("arrendador_id")).isEqualTo(ana.getId());
        assertThat(fila.get("creado_en")).isEqualTo(antes.get("creado_en"));
        assertThat(instante(fila.get("actualizado_en"))).isEqualTo(reloj.instant());
        assertThat(espacios()).hasSize(1);
    }

    @Test
    @DisplayName("HU-07 R22: editar un PUBLICADO deja los nuevos valores en el detalle y el listado, aún PUBLICADO")
    void editarPublicado() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        forzarEstado(id, "PUBLICADO");

        editar(token, id, ficha("precioHora", 95.5)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        consultar(token, id).andExpect(jsonPath("$.precioHora").value(95.5))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
        listar(token).andExpect(jsonPath("$[0].precioHora").value(95.5))
                .andExpect(jsonPath("$[0].estado").value("PUBLICADO"));
        assertThat(espacio(id).get("estado")).isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("HU-07 R23: editar un espacio en BAJA u OCULTO responde estado no editable; uno PAUSADO se edita")
    void estadoNoEditable() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        for (var estado : List.of("BAJA", "OCULTO")) {
            var id = crearEspacio(token);
            forzarEstado(id, estado);
            var antes = espacio(id);

            error(editar(token, id, ficha("nombre", "Otro nombre")), ESTADO_NO_EDITABLE);

            assertThat(espacio(id)).isEqualTo(antes);
        }
        var pausado = crearEspacio(token);
        forzarEstado(pausado, "PAUSADO");
        editar(token, pausado, ficha("nombre", "Otro nombre")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADO"));
    }

    @Test
    @DisplayName("HU-07 R24: el precio se guarda como NUMERIC(8,2) igual al recibido")
    void precioNumerico() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);

        var id = ((Number) mapa(crear(token, ficha("precioHora", 80.5)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id")).longValue();

        assertThat((BigDecimal) espacio(id).get("precio_hora")).isEqualByComparingTo("80.50").hasToString("80.50");
        var columna = jdbc.queryForMap("SELECT data_type, numeric_precision, numeric_scale FROM "
                + "information_schema.columns WHERE table_name = 'espacio' AND column_name = 'precio_hora'");
        assertThat(columna).containsEntry("data_type", "numeric").containsEntry("numeric_precision", 8)
                .containsEntry("numeric_scale", 2);
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R45: crear audita CREACION_ESPACIO con usuario, entidad, IP, fecha y detalle exacto")
    void auditaCreacion() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        var auditoria = auditorias("CREACION_ESPACIO");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "tipo=CANCHA;zona=LOS_OLIVOS;capacidad=20;precioHora=80.00");
        assertThat(instante(auditoria.get(0).get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-07 R46, R47: editar audita cada campo cambiado; una edición idéntica no audita")
    void auditaEdicion() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var cuerpo = ficha("precioHora", 90);
        cuerpo.put("descripcion", "Una descripción distinta y más larga.");

        editar(token, id, cuerpo).andExpect(status().isOk());
        var actualizado = espacio(id).get("actualizado_en");
        reloj.avanzar(Duration.ofMinutes(5));
        editar(token, id, cuerpo).andExpect(status().isOk());

        var auditoria = auditorias("EDICION_ESPACIO");
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "precioHora=80.00->90.00;descripcion");
        assertThat(espacio(id).get("actualizado_en")).isEqualTo(actualizado);
    }

    // ── Propiedad y acceso (STRIDE E) ────────────────────────────────────

    @Test
    @DisplayName("HU-07 R40: un espacio ajeno o inexistente responde 404 en consulta, edición y publicación")
    void ajenoOInexistente() throws Exception {
        crearArrendador(CORREO);
        var beto = crearArrendador(OTRO_CORREO);
        forzarIdentidad(beto, "VERIFICADA");
        var deAna = crearEspacio(token(CORREO));
        var antes = espacio(deAna);
        var tokenBeto = token(OTRO_CORREO);

        for (var id : List.of(deAna, 999_999L)) {
            for (var respuesta : List.of(consultar(tokenBeto, id), editar(tokenBeto, id, ficha()),
                    publicar(tokenBeto, id))) {
                respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            }
        }
        assertThat(espacio(deAna)).isEqualTo(antes);
        assertThat(auditorias("EDICION_ESPACIO")).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R53: un espacio ajeno en BAJA responde 404, no estado no editable")
    void propiedadAntesQueEstado() throws Exception {
        crearArrendador(CORREO);
        crearArrendador(OTRO_CORREO);
        var deAna = crearEspacio(token(CORREO));
        forzarEstado(deAna, "BAJA");

        editar(token(OTRO_CORREO), deAna, ficha()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-07 R41: una cuenta USUARIO recibe 403 en cada endpoint de espacios")
    void rolUsuario() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));
        crearUsuario(CORREO_USUARIO);
        var token = token(CORREO_USUARIO);

        for (var respuesta : List.of(listar(token), crear(token, ficha()), consultar(token, id),
                editar(token, id, ficha()), publicar(token, id),
                subir(token, id, foto("a.jpg", "image/jpeg", ArchivosDePrueba.jpeg(10, 10))), quitar(token, id, 1))) {
            respuesta.andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }
        assertThat(espacios()).hasSize(1);
    }

    @Test
    @DisplayName("HU-07 R42: sin correo verificado se rechazan crear, editar, publicar y fotos; la consulta sigue")
    void correoNoVerificado() throws Exception {
        var ana = crearArrendador(CORREO);
        forzarIdentidad(ana, "VERIFICADA");
        var token = token(CORREO);
        var id = crearEspacio(token);
        quitarCorreoVerificado(ana);

        for (var respuesta : List.of(crear(token, ficha()), editar(token, id, ficha("nombre", "Otro nombre")),
                publicar(token, id), subir(token, id, foto("a.jpg", "image/jpeg", ArchivosDePrueba.jpeg(10, 10))),
                quitar(token, id, 1))) {
            respuesta.andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }
        listar(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        consultar(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Cancha Los Olivos"));
        assertThat(espacios()).hasSize(1);
        assertThat(espacio(id).get("estado")).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("HU-07 R43: sin sesión cada endpoint de espacios responde 401 con el mensaje de sesión inválida")
    void sinSesion() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));
        var solicitudes = List.of(get(ESPACIOS), get(ESPACIOS + "/" + id),
                post(ESPACIOS).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ficha())),
                put(ESPACIOS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ficha())),
                post(ESPACIOS + "/" + id + "/publicacion"),
                multipart(ESPACIOS + "/" + id + "/fotos")
                        .file(foto("a.jpg", "image/jpeg", ArchivosDePrueba.jpeg(10, 10))),
                delete(ESPACIOS + "/" + id + "/fotos/1"));

        for (var solicitud : solicitudes) {
            mvc.perform(solicitud).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        }
        assertThat(espacios()).hasSize(1);
    }

    @Test
    @DisplayName("HU-07 R52: una ficha inválida responde 400 por campo aunque la cuenta no sea ARRENDADOR")
    void fichaAntesQueRol() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var token = token(CORREO_USUARIO);

        crear(token, ficha("nombre", "ab")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        editar(token, 999_999L, ficha("tipo", "PISCINA")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("tipo"));
    }
}
