package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;

/** HU-21: pausa, reanudación y baja del arrendador, con el doble del puerto de reservas (DT-12). IP 10.21.x.x. */
class EstadoEspacioControllerIT extends EspaciosControllerSoporte {

    private static final String SOLO_PUBLICADO = "Solo puedes pausar un espacio publicado.";
    private static final String SOLO_PAUSADO = "Solo puedes reanudar un espacio pausado.";
    private static final String BAJA_NO_PERMITIDA = "Solo puedes dar de baja un espacio publicado o pausado.";
    private static final String IDENTIDAD_NO_VERIFICADA =
            "Para publicar tus espacios, primero debes verificar tu identidad.";
    private static final String SIN_FOTOS = "Agrega al menos una foto antes de publicar.";
    private static final String ESTADO_NO_EDITABLE = "Este espacio ya no se puede editar.";
    private static final String RESERVAS_POR_TERMINAR = "Este espacio tiene reservas por terminar. Cancela sus "
            + "reservas futuras (con reembolso del 100 %) y vuelve a intentarlo. Si hay una en curso o pendiente de "
            + "pago, espera a que termine o venza.";
    private static final List<String> ACCIONES = List.of("PAUSA_ESPACIO", "REANUDACION_ESPACIO", "BAJA_ESPACIO");
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @BeforeEach
    void ipPropia() {
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.21." + (n / 250) + "." + (n % 250 + 1);
    }

    private String tokenAna;
    private long espacioId;

    private void anaConPublicado() throws Exception {
        var ana = crearArrendador(CORREO);
        tokenAna = token(CORREO);
        espacioId = crearEspacio(tokenAna);
        subir(tokenAna, espacioId, foto("cancha.jpg", "image/jpeg", ArchivosDePrueba.jpeg(40, 30)))
                .andExpect(status().isCreated());
        forzarIdentidad(ana, "VERIFICADA");
        publicar(tokenAna, espacioId).andExpect(status().isOk());
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 8, 22)",
                espacioId, enUnaSemana().getDayOfWeek().getValue());
        reloj.avanzar(Duration.ofMinutes(5));
    }

    private LocalDate enUnaSemana() {
        return ZonaHoraria.hoy(reloj).plusDays(7);
    }

    private ResultActions accion(String token, long id, String ruta) throws Exception {
        return mvc.perform(con(token, post(ESPACIOS + "/" + id + "/" + ruta)));
    }

    private ResultActions pausar(String token, long id) throws Exception {
        return accion(token, id, "pausa");
    }

    private ResultActions reanudar(String token, long id) throws Exception {
        return accion(token, id, "reanudacion");
    }

    private ResultActions darDeBaja(String token, long id) throws Exception {
        return accion(token, id, "baja");
    }

    private ResultActions buscar() throws Exception {
        return mvc.perform(con(null, get("/api/espacios?tipo=CANCHA&zona=LOS_OLIVOS&fecha=" + enUnaSemana())));
    }

    private ResultActions fichaPublica(long id) throws Exception {
        return mvc.perform(con(null, get("/api/espacios/" + id)));
    }

    private ResultActions reservar(String token, long id) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("espacioId", id);
        cuerpo.put("fecha", enUnaSemana().toString());
        cuerpo.put("horas", List.of(10));
        return mvc.perform(con(token, post("/api/reservas")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private static void error(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores.length()").value(0));
    }

    private static void errorEnReservas(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(RESERVAS_POR_TERMINAR));
    }

    private int auditoriasDeEstado() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion IN (?, ?, ?)", Integer.class,
                ACCIONES.toArray());
    }

    // ── Pausar (CA1) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R1, R34, R39: pausar un PUBLICADO propio responde 200 PAUSADO, guarda la fila y audita")
    void pausaUnPublicado() throws Exception {
        anaConPublicado();

        pausar(tokenAna, espacioId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(espacioId))
                .andExpect(jsonPath("$.estado").value("PAUSADO"))
                .andExpect(jsonPath("$.fotos.length()").value(1));

        var fila = espacio(espacioId);
        assertThat(fila.get("estado")).isEqualTo("PAUSADO");
        assertThat(instante(fila.get("actualizado_en"))).isEqualTo(reloj.instant());
        var auditadas = auditorias("PAUSA_ESPACIO");
        assertThat(auditadas).hasSize(1);
        var auditoria = auditadas.getFirst();
        assertThat(auditoria.get("entidad")).isEqualTo("espacio");
        assertThat(((Number) auditoria.get("entidad_id")).longValue()).isEqualTo(espacioId);
        assertThat(auditoria.get("usuario_id")).isEqualTo(usuarios.findByCorreo(CORREO).orElseThrow().getId());
        assertThat(auditoria.get("ip")).isEqualTo(ip);
        assertThat(auditoria.get("detalle")).isEqualTo("estado=PUBLICADO->PAUSADO");
    }

    @Test
    @DisplayName("HU-21 R2, R3, R4: un PAUSADO no aparece en la búsqueda, su ficha da 404 y no recibe reservas")
    void pausadoNoSeVeNiSeReserva() throws Exception {
        anaConPublicado();
        crearUsuario(CORREO_USUARIO);
        var tokenCarla = token(CORREO_USUARIO);
        buscar().andExpect(status().isOk()).andExpect(jsonPath("$.resultados.length()").value(1));
        fichaPublica(espacioId).andExpect(status().isOk());

        pausar(tokenAna, espacioId).andExpect(status().isOk());

        buscar().andExpect(status().isOk()).andExpect(jsonPath("$.resultados.length()").value(0));
        fichaPublica(espacioId).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        reservar(tokenCarla, espacioId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_bloque", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-21 R2, R3, R4: un espacio en BAJA no aparece en la búsqueda, su ficha da 404 y no recibe reservas")
    void bajaNoSeVeNiSeReserva() throws Exception {
        anaConPublicado();
        crearUsuario(CORREO_USUARIO);
        var tokenCarla = token(CORREO_USUARIO);

        darDeBaja(tokenAna, espacioId).andExpect(status().isOk());

        buscar().andExpect(status().isOk()).andExpect(jsonPath("$.resultados.length()").value(0));
        fichaPublica(espacioId).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        reservar(tokenCarla, espacioId).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-21 R6, R38: pausar un BORRADOR, un PAUSADO o un BAJA da 400 sin cambios ni auditoría")
    void pausarFueraDePublicado() throws Exception {
        anaConPublicado();
        var borrador = crearEspacio(tokenAna);
        error(pausar(tokenAna, borrador), SOLO_PUBLICADO);

        for (var estado : List.of("PAUSADO", "BAJA")) {
            forzarEstado(espacioId, estado);
            var antes = espacio(espacioId).get("actualizado_en");
            error(pausar(tokenAna, espacioId), SOLO_PUBLICADO);
            assertThat(espacio(espacioId).get("estado")).isEqualTo(estado);
            assertThat(espacio(espacioId).get("actualizado_en")).isEqualTo(antes);
        }
        assertThat(espacio(borrador).get("estado")).isEqualTo("BORRADOR");
        assertThat(auditoriasDeEstado()).isZero();
    }

    // ── Reanudar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R7, R35: reanudar un PAUSADO con identidad y foto lo deja PUBLICADO y vuelve a la búsqueda")
    void reanudaUnPausado() throws Exception {
        anaConPublicado();
        pausar(tokenAna, espacioId).andExpect(status().isOk());
        reloj.avanzar(Duration.ofMinutes(5));

        reanudar(tokenAna, espacioId).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        var fila = espacio(espacioId);
        assertThat(fila.get("estado")).isEqualTo("PUBLICADO");
        assertThat(instante(fila.get("actualizado_en"))).isEqualTo(reloj.instant());
        buscar().andExpect(jsonPath("$.resultados.length()").value(1));
        var auditadas = auditorias("REANUDACION_ESPACIO");
        assertThat(auditadas).hasSize(1);
        assertThat(auditadas.getFirst().get("detalle")).isEqualTo("estado=PAUSADO->PUBLICADO");
        assertThat(auditadas.getFirst().get("ip")).isEqualTo(ip);
    }

    @Test
    @DisplayName("HU-21 R8: reanudar un PUBLICADO o un BAJA da 400 solo pausado")
    void reanudarFueraDePausado() throws Exception {
        anaConPublicado();
        error(reanudar(tokenAna, espacioId), SOLO_PAUSADO);
        forzarEstado(espacioId, "BAJA");
        error(reanudar(tokenAna, espacioId), SOLO_PAUSADO);
        assertThat(espacio(espacioId).get("estado")).isEqualTo("BAJA");
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R9, R11: reanudar con la identidad EN_REVISION da 400 identidad, aunque falten fotos")
    void reanudarSinIdentidad() throws Exception {
        anaConPublicado();
        pausar(tokenAna, espacioId).andExpect(status().isOk());
        forzarIdentidad(usuarios.findByCorreo(CORREO).orElseThrow(), "EN_REVISION");
        jdbc.update("DELETE FROM foto_espacio WHERE espacio_id = ?", espacioId);

        error(reanudar(tokenAna, espacioId), IDENTIDAD_NO_VERIFICADA);

        assertThat(espacio(espacioId).get("estado")).isEqualTo("PAUSADO");
        assertThat(auditorias("REANUDACION_ESPACIO")).isEmpty();
    }

    @Test
    @DisplayName("HU-21 R10: reanudar un PAUSADO sin fotos da 400 sin fotos")
    void reanudarSinFotos() throws Exception {
        anaConPublicado();
        pausar(tokenAna, espacioId).andExpect(status().isOk());
        jdbc.update("DELETE FROM foto_espacio WHERE espacio_id = ?", espacioId);

        error(reanudar(tokenAna, espacioId), SIN_FOTOS);

        assertThat(espacio(espacioId).get("estado")).isEqualTo("PAUSADO");
    }

    @Test
    @DisplayName("HU-21 R11: con el espacio PUBLICADO y sin identidad ni fotos, gana el mensaje del estado")
    void reanudarOrdenEstadoPrimero() throws Exception {
        anaConPublicado();
        forzarIdentidad(usuarios.findByCorreo(CORREO).orElseThrow(), "EN_REVISION");
        jdbc.update("DELETE FROM foto_espacio WHERE espacio_id = ?", espacioId);

        error(reanudar(tokenAna, espacioId), SOLO_PAUSADO);
    }

    // ── Dar de baja (CA2) ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R12, R36, R39: dar de baja un PUBLICADO y un PAUSADO sin reservas responde 200 BAJA y audita")
    void daDeBajaPublicadoYPausado() throws Exception {
        anaConPublicado();
        var otro = crearEspacio(tokenAna);
        forzarEstado(otro, "PAUSADO");

        darDeBaja(tokenAna, espacioId).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BAJA"));
        darDeBaja(tokenAna, otro).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BAJA"));

        assertThat(espacio(espacioId).get("estado")).isEqualTo("BAJA");
        assertThat(instante(espacio(espacioId).get("actualizado_en"))).isEqualTo(reloj.instant());
        assertThat(espacio(otro).get("estado")).isEqualTo("BAJA");
        assertThat(auditorias("BAJA_ESPACIO")).extracting(fila -> fila.get("detalle"))
                .containsExactly("estado=PUBLICADO->BAJA", "estado=PAUSADO->BAJA");
    }

    @Test
    @DisplayName("HU-21 R13, R38: dar de baja con una reserva futura da 400 en reservas sin cambios ni auditoría")
    void bajaConReservaFutura() throws Exception {
        anaConPublicado();
        reservasFalsas.cargar(new BloqueReservado(ZonaHoraria.hoy(reloj).plusDays(3), 10, true));
        var antes = espacio(espacioId).get("actualizado_en");

        errorEnReservas(darDeBaja(tokenAna, espacioId));

        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(espacio(espacioId).get("actualizado_en")).isEqualTo(antes);
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R16: dar de baja un BORRADOR o un BAJA da 400 sin cambios")
    void bajaDeEstadoNoAdmitido() throws Exception {
        anaConPublicado();
        var borrador = crearEspacio(tokenAna);
        error(darDeBaja(tokenAna, borrador), BAJA_NO_PERMITIDA);

        darDeBaja(tokenAna, espacioId).andExpect(status().isOk());
        error(darDeBaja(tokenAna, espacioId), BAJA_NO_PERMITIDA);

        assertThat(espacio(borrador).get("estado")).isEqualTo("BORRADOR");
        assertThat(auditorias("BAJA_ESPACIO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-21 R18: editar la ficha de un espacio en BAJA da 400 este espacio ya no se puede editar")
    void bajaNoSeEdita() throws Exception {
        anaConPublicado();
        darDeBaja(tokenAna, espacioId).andExpect(status().isOk());

        error(editar(tokenAna, espacioId, ficha("nombre", "Otro nombre")), ESTADO_NO_EDITABLE);

        assertThat(espacio(espacioId).get("nombre")).isEqualTo("Cancha Los Olivos");
    }

    // ── Control de acceso ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R25: un USUARIO recibe 403 en las tres rutas y el espacio no cambia")
    void usuarioRecibe403() throws Exception {
        anaConPublicado();
        crearUsuario(CORREO_USUARIO);
        var tokenCarla = token(CORREO_USUARIO);

        for (var respuesta : List.of(pausar(tokenCarla, espacioId), reanudar(tokenCarla, espacioId),
                darDeBaja(tokenCarla, espacioId))) {
            respuesta.andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R26: un arrendador sin correo verificado recibe 403 y el espacio no cambia")
    void sinCorreoVerificadoRecibe403() throws Exception {
        anaConPublicado();
        quitarCorreoVerificado(usuarios.findByCorreo(CORREO).orElseThrow());

        for (var respuesta : List.of(pausar(tokenAna, espacioId), reanudar(tokenAna, espacioId),
                darDeBaja(tokenAna, espacioId))) {
            respuesta.andExpect(status().isForbidden());
        }
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R27, R38: un espacio ajeno o inexistente da 404 genérico sin cambios ni auditoría")
    void ajenoOInexistenteDa404() throws Exception {
        anaConPublicado();
        crearArrendador(OTRO_CORREO);
        var tokenBeto = token(OTRO_CORREO);
        var antes = espacio(espacioId).get("actualizado_en");

        for (var respuesta : List.of(pausar(tokenBeto, espacioId), reanudar(tokenBeto, espacioId),
                darDeBaja(tokenBeto, espacioId), pausar(tokenAna, espacioId + 1000))) {
            respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(espacio(espacioId).get("actualizado_en")).isEqualTo(antes);
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R38: sin sesión las tres rutas responden 401 y no cambian nada")
    void sinSesionRecibe401() throws Exception {
        anaConPublicado();

        for (var respuesta : List.of(pausar(null, espacioId), reanudar(null, espacioId), darDeBaja(null, espacioId))) {
            respuesta.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        }
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(auditoriasDeEstado()).isZero();
    }

    // ── Listado propio ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R40: Mis espacios incluye los PAUSADO y los BAJA con su estado")
    void listadoIncluyePausadoYBaja() throws Exception {
        anaConPublicado();
        reloj.avanzar(Duration.ofMinutes(1));
        var otro = crearEspacio(tokenAna);
        forzarEstado(otro, "PUBLICADO");
        pausar(tokenAna, espacioId).andExpect(status().isOk());
        darDeBaja(tokenAna, otro).andExpect(status().isOk());

        var lista = lista(listar(tokenAna).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());

        assertThat(lista).extracting(fila -> ((Number) fila.get("id")).longValue(), fila -> fila.get("estado"))
                .containsExactly(tuple(otro, "BAJA"),
                        tuple(espacioId, "PAUSADO"));
    }
}
