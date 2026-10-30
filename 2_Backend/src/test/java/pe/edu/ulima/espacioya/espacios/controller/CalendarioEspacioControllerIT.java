package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;

class CalendarioEspacioControllerIT extends EspaciosControllerSoporte {

    private static final String RANGO_HORAS = "La hora de fin debe ser posterior a la de inicio.";
    private static final String SUPERPUESTAS = "Esta franja se cruza con otra del mismo día.";
    private static final String FECHA_INVALIDA = "Elige una fecha válida.";
    private static final String FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";
    private static final String YA_BLOQUEADA = "Esta fecha ya está bloqueada.";
    private static final String RESERVA_PAGADA = "No puedes bloquear esta fecha porque tiene reservas pagadas.";
    private static final String RESERVA_PENDIENTE =
            "Esta fecha tiene una reserva en proceso de pago. Inténtalo de nuevo en unos minutos.";
    private static final String ESTADO_NO_EDITABLE = "Este espacio ya no se puede editar.";
    private static final String M_HORA_INICIO = "La hora de inicio debe ser una hora en punto entre 00:00 y 23:00.";
    private static final String M_HORA_FIN = "La hora de fin debe ser una hora en punto entre 01:00 y 24:00.";
    private static final String M_DIA = "Elige un día de la semana.";
    private static final String M_FRANJAS = "Envía la lista de franjas del horario (como mucho 168).";
    private static final String M_FRANJA = "Completa la franja.";
    private static final String HORARIO = "HORARIO_ESPACIO_ACTUALIZADO";
    private static final String BLOQUEO_CREADO = "BLOQUEO_FECHA_CREADO";
    private static final String BLOQUEO_ELIMINADO = "BLOQUEO_FECHA_ELIMINADO";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @BeforeEach
    void usarIpsDeHu08() {
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.8." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private LocalDate hoy() {
        return ZonaHoraria.hoy(reloj);
    }

    private static Map<String, Object> franja(Object dia, Object inicio, Object fin) {
        var franja = new LinkedHashMap<String, Object>();
        franja.put("diaSemana", dia);
        franja.put("horaInicio", inicio);
        franja.put("horaFin", fin);
        return franja;
    }

    @SafeVarargs
    private static Map<String, Object> horario(Map<String, Object>... franjas) {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("franjas", new ArrayList<>(List.of(franjas)));
        return cuerpo;
    }

    private static Map<String, Object> lunesAViernes() {
        return horario(franja(3, 8, 22), franja(1, 8, 22), franja(5, 8, 22), franja(2, 8, 22), franja(4, 8, 22));
    }

    private static int dia(LocalDate fecha) {
        return fecha.getDayOfWeek().getValue();
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private String ruta(long id, String recurso) {
        return ESPACIOS + "/" + id + recurso;
    }

    private ResultActions ponerHorario(String token, long id, Object cuerpo) throws Exception {
        return mvc.perform(con(token, put(ruta(id, "/horario"))).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo instanceof String texto ? texto : json.writeValueAsString(cuerpo)));
    }

    private ResultActions verHorario(String token, long id) throws Exception {
        return mvc.perform(con(token, get(ruta(id, "/horario"))));
    }

    private ResultActions bloquear(String token, long id, Object fecha) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        if (fecha != null) {
            cuerpo.put("fecha", fecha.toString());
        }
        return bloquearCon(token, id, cuerpo);
    }

    private ResultActions bloquearCon(String token, long id, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(con(token, post(ruta(id, "/bloqueos"))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions verBloqueos(String token, long id) throws Exception {
        return mvc.perform(con(token, get(ruta(id, "/bloqueos"))));
    }

    private ResultActions desbloquear(String token, long id, Object fecha) throws Exception {
        return mvc.perform(con(token, delete(ruta(id, "/bloqueos/" + fecha))));
    }

    private ResultActions verDia(String token, long id, Object fecha) throws Exception {
        var solicitud = get(ruta(id, "/disponibilidad"));
        if (fecha != null) {
            solicitud.param("fecha", fecha.toString());
        }
        return mvc.perform(con(token, solicitud));
    }

    private void campoFecha(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private void sinCampo(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    private String cuerpo(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private List<List<Integer>> filasHorario(long id) {
        return jdbc.queryForList("SELECT dia_semana, hora_inicio, hora_fin FROM horario_semanal WHERE espacio_id = ?"
                + " ORDER BY dia_semana, hora_inicio", id).stream()
                .map(fila -> List.of(entero(fila.get("dia_semana")), entero(fila.get("hora_inicio")),
                        entero(fila.get("hora_fin"))))
                .toList();
    }

    private List<LocalDate> fechasBloqueadas(long id) {
        return jdbc.queryForList("SELECT fecha FROM bloqueo_fecha WHERE espacio_id = ? ORDER BY fecha",
                Date.class, id).stream().map(Date::toLocalDate).toList();
    }

    private void insertarBloqueo(long id, LocalDate fecha) {
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", id, Date.valueOf(fecha));
    }

    private static int entero(Object valor) {
        return ((Number) valor).intValue();
    }

    private static List<Integer> fila(int dia, int inicio, int fin) {
        return List.of(dia, inicio, fin);
    }

    // ── Horario semanal (CA1) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R1, R2: guardar lunes a viernes 8–22 responde ordenado y deja una fila por franja")
    void guardaHorario() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(5))
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(1))
                .andExpect(jsonPath("$.franjas[1].diaSemana").value(2))
                .andExpect(jsonPath("$.franjas[4].diaSemana").value(5))
                .andExpect(jsonPath("$.franjas[4].horaInicio").value(8))
                .andExpect(jsonPath("$.franjas[4].horaFin").value(22));

        assertThat(filasHorario(id)).containsExactly(fila(1, 8, 22), fila(2, 8, 22), fila(3, 8, 22),
                fila(4, 8, 22), fila(5, 8, 22));
    }

    @Test
    @DisplayName("HU-08 R1, R2: un segundo horario reemplaza al primero, aunque una franja empiece a la misma hora")
    void reemplazaHorario() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk());

        ponerHorario(token, id, horario(franja(6, 15, 24), franja(1, 8, 13), franja(6, 8, 13)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(3))
                .andExpect(jsonPath("$.franjas[0].horaFin").value(13))
                .andExpect(jsonPath("$.franjas[2].horaInicio").value(15));

        assertThat(filasHorario(id)).containsExactly(fila(1, 8, 13), fila(6, 8, 13), fila(6, 15, 24));
    }

    @Test
    @DisplayName("HU-08 R3: una lista vacía deja el espacio sin horario")
    void vaciaHorario() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk());

        ponerHorario(token, id, horario()).andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(0));

        assertThat(filasHorario(id)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R4, R7: un decimal, un texto o un booleano no son una hora; el horario no cambia")
    void rechazaHorasQueNoSonEnteras() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk());

        for (var valor : new Object[] {8.5, "8", true}) {
            ponerHorario(token, id, horario(franja(1, valor, 12))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("franjas[0].horaInicio"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(M_HORA_INICIO));
        }

        assertThat(filasHorario(id)).hasSize(5);
    }

    @Test
    @DisplayName("HU-08 R4: un error por cada campo inválido, por elemento nulo y por franjas ausente")
    void unErrorPorCampo() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        var respuesta = ponerHorario(token, id, horario(franja(1, 8, 12), franja(0, 24, 25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(3));
        assertThat(cuerpo(respuesta)).contains("franjas[1].diaSemana", M_DIA, "franjas[1].horaInicio",
                M_HORA_INICIO, "franjas[1].horaFin", M_HORA_FIN);

        ponerHorario(token, id, "{\"franjas\":[null]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("franjas[0]"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(M_FRANJA));
        ponerHorario(token, id, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("franjas"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(M_FRANJAS));
        assertThat(filasHorario(id)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R5, R7: fin igual al inicio responde rango de horas en franjas[i] sin cambiar el horario")
    void rechazaRango() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk());

        ponerHorario(token, id, horario(franja(1, 8, 12), franja(2, 10, 10))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("franjas[1]"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(RANGO_HORAS));

        assertThat(filasHorario(id)).hasSize(5);
    }

    @Test
    @DisplayName("HU-08 R6, R7: franjas que se cruzan responden superpuestas en la de índice mayor")
    void rechazaSuperpuestas() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk());

        ponerHorario(token, id, horario(franja(1, 8, 12), franja(1, 12, 14), franja(1, 11, 13)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("franjas[2]"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(SUPERPUESTAS));

        assertThat(filasHorario(id)).containsExactly(fila(1, 8, 22), fila(2, 8, 22), fila(3, 8, 22),
                fila(4, 8, 22), fila(5, 8, 22));
    }

    @Test
    @DisplayName("HU-08 R8: consulta el horario vacío, luego ordenado, y también con el espacio de baja")
    void consultaHorario() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        verHorario(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.franjas.length()").value(0));

        ponerHorario(token, id, horario(franja(6, 15, 24), franja(1, 8, 13))).andExpect(status().isOk());
        forzarEstado(id, "BAJA");

        verHorario(token, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(2))
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(1))
                .andExpect(jsonPath("$.franjas[1].diaSemana").value(6))
                .andExpect(jsonPath("$.franjas[1].horaFin").value(24));
    }

    @Test
    @DisplayName("HU-08 R9: la base rechaza día 8, fin 25, inicio igual al fin e inicio repetido")
    void baseValidaElHorario() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));
        var insertar = "INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin)"
                + " VALUES (?, ?, ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insertar, id, 8, 8, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, 0, 8, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, 1, 8, 25))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, 1, -1, 3))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, 1, 10, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insertar, id, 1, 8, 10);
        assertThatThrownBy(() -> jdbc.update(insertar, id, 1, 8, 12))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Bloqueo de fechas (CA2) ──────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R10, R11, R43: bloquear una fecha responde 201, guarda la fila y la audita")
    void bloqueaFecha() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(3);

        bloquear(token, id, fecha).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fecha").value(fecha.toString()));

        assertThat(fechasBloqueadas(id)).containsExactly(fecha);
        var auditoria = auditorias(BLOQUEO_CREADO);
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "fecha=" + fecha);
        assertThat(instante(auditoria.get(0).get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-08 R12: fecha inexistente, mal escrita o ausente responde fecha inválida en el campo fecha")
    void rechazaFechaInvalida() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        campoFecha(bloquear(token, id, "2026-02-30"), FECHA_INVALIDA);
        campoFecha(bloquear(token, id, "07/11/2026"), FECHA_INVALIDA);
        campoFecha(bloquear(token, id, null), FECHA_INVALIDA);
        campoFecha(verDia(token, id, "abc"), FECHA_INVALIDA);
        campoFecha(verDia(token, id, null), FECHA_INVALIDA);
        campoFecha(desbloquear(token, id, "abc"), FECHA_INVALIDA);
        assertThat(fechasBloqueadas(id)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R13: solo se bloquean fechas de hoy a hoy + 55 días")
    void soloDentroDeLaVentana() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        campoFecha(bloquear(token, id, hoy().minusDays(1)), FUERA_DE_VENTANA);
        campoFecha(bloquear(token, id, hoy().plusDays(56)), FUERA_DE_VENTANA);
        bloquear(token, id, hoy()).andExpect(status().isCreated());
        bloquear(token, id, hoy().plusDays(55)).andExpect(status().isCreated());

        assertThat(fechasBloqueadas(id)).containsExactly(hoy(), hoy().plusDays(55));
    }

    @Test
    @DisplayName("HU-08 R14: bloquear dos veces la misma fecha responde ya bloqueada")
    void yaBloqueada() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(10);

        bloquear(token, id, fecha).andExpect(status().isCreated());
        campoFecha(bloquear(token, id, fecha), YA_BLOQUEADA);

        assertThat(fechasBloqueadas(id)).containsExactly(fecha);
        assertThat(auditorias(BLOQUEO_CREADO)).hasSize(1);
    }

    @Test
    @DisplayName("HU-08 R15: la lista trae la ventana y las fechas desde hoy en orden, sin las pasadas")
    void listaBloqueos() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        insertarBloqueo(id, hoy().plusDays(20));
        insertarBloqueo(id, hoy().minusDays(2));
        insertarBloqueo(id, hoy());

        verBloqueos(token, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(hoy().toString()))
                .andExpect(jsonPath("$.hasta").value(hoy().plusDays(55).toString()))
                .andExpect(jsonPath("$.fechas.length()").value(2))
                .andExpect(jsonPath("$.fechas[0]").value(hoy().toString()))
                .andExpect(jsonPath("$.fechas[1]").value(hoy().plusDays(20).toString()));
    }

    @Test
    @DisplayName("HU-08 R16, R44: desbloquear responde 204, borra la fila y lo audita")
    void desbloqueaFecha() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(4);
        bloquear(token, id, fecha).andExpect(status().isCreated());

        desbloquear(token, id, fecha).andExpect(status().isNoContent());

        assertThat(fechasBloqueadas(id)).isEmpty();
        var auditoria = auditorias(BLOQUEO_ELIMINADO);
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "fecha=" + fecha);
    }

    @Test
    @DisplayName("HU-08 R17: desbloquear una fecha no bloqueada es 404 y no toca otros bloqueos")
    void desbloquearNoBloqueada() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var otra = hoy().plusDays(6);
        bloquear(token, id, otra).andExpect(status().isCreated());

        desbloquear(token, id, hoy().plusDays(7)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));

        assertThat(fechasBloqueadas(id)).containsExactly(otra);
        assertThat(auditorias(BLOQUEO_ELIMINADO)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R18: la base rechaza dos bloqueos de la misma fecha y espacio")
    void baseImpideBloqueoDuplicado() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));
        insertarBloqueo(id, hoy().plusDays(2));

        assertThatThrownBy(() -> insertarBloqueo(id, hoy().plusDays(2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Conflicto con reservas (CA3) ─────────────────────────────────────

    @Test
    @DisplayName("HU-08 R19: una fecha con una reserva pagada no se bloquea y se avisa el conflicto")
    void reservaPagadaImpideBloquear() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(9);
        reservasFalsas.cargar(new BloqueReservado(fecha, 18, true));

        sinCampo(bloquear(token, id, fecha), RESERVA_PAGADA);

        assertThat(fechasBloqueadas(id)).isEmpty();
        assertThat(auditorias(BLOQUEO_CREADO)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R20: una fecha con una reserva pendiente de pago no se bloquea")
    void reservaPendienteImpideBloquear() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(9);
        reservasFalsas.cargar(new BloqueReservado(fecha, 18, false));

        sinCampo(bloquear(token, id, fecha), RESERVA_PENDIENTE);
        bloquear(token, id, fecha.plusDays(1)).andExpect(status().isCreated());

        assertThat(fechasBloqueadas(id)).containsExactly(fecha.plusDays(1));
    }

    // ── Bloques del día ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R22, R26, R45: los bloques del día salen en orden, con hora de fin y LIBRE")
    void bloquesDelDia() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(7);
        ponerHorario(token, id, horario(franja(dia(fecha), 14, 16), franja(dia(fecha), 8, 10)))
                .andExpect(status().isOk());

        var respuesta = verDia(token, id, fecha).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.bloqueada").value(false))
                .andExpect(jsonPath("$.bloques.length()").value(4))
                .andExpect(jsonPath("$.bloques[0].horaInicio").value(8))
                .andExpect(jsonPath("$.bloques[0].horaFin").value(9))
                .andExpect(jsonPath("$.bloques[3].horaInicio").value(15))
                .andExpect(jsonPath("$.bloques[3].horaFin").value(16));

        var dto = mapa(cuerpo(respuesta));
        assertThat(dto).containsOnlyKeys("fecha", "bloqueada", "bloques");
        @SuppressWarnings("unchecked")
        var bloques = (List<Map<String, Object>>) dto.get("bloques");
        assertThat(bloques).allSatisfy(bloque -> {
            assertThat(bloque).containsOnlyKeys("horaInicio", "horaFin", "estado");
            assertThat(bloque).containsEntry("estado", "LIBRE");
        });
    }

    @Test
    @DisplayName("HU-08 R23: tras bloquear la fecha todos sus bloques quedan BLOQUEADO")
    void bloqueadaEnElDia() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(7);
        ponerHorario(token, id, horario(franja(dia(fecha), 8, 11))).andExpect(status().isOk());
        bloquear(token, id, fecha).andExpect(status().isCreated());

        verDia(token, id, fecha).andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueada").value(true))
                .andExpect(jsonPath("$.bloques.length()").value(3))
                .andExpect(jsonPath("$.bloques[0].estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.bloques[1].estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.bloques[2].estado").value("BLOQUEADO"));
    }

    @Test
    @DisplayName("HU-08 R24, R45: un bloque retenido por una reserva sale RESERVADO, sin datos de la reserva")
    void reservadoEnElDia() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(8);
        ponerHorario(token, id, horario(franja(dia(fecha), 8, 11))).andExpect(status().isOk());
        reservasFalsas.cargar(new BloqueReservado(fecha, 9, true));

        var respuesta = verDia(token, id, fecha).andExpect(status().isOk())
                .andExpect(jsonPath("$.bloques[0].estado").value("LIBRE"))
                .andExpect(jsonPath("$.bloques[1].estado").value("RESERVADO"))
                .andExpect(jsonPath("$.bloques[2].estado").value("LIBRE"));

        assertThat(cuerpo(respuesta)).doesNotContain("pagado", "usuario", "monto", "arrendador", "\"id\"");
    }

    @Test
    @DisplayName("HU-08 R27: consultar un día fuera de la ventana responde fuera de ventana")
    void diaFueraDeVentana() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        campoFecha(verDia(token, id, hoy().minusDays(1)), FUERA_DE_VENTANA);
        campoFecha(verDia(token, id, hoy().plusDays(56)), FUERA_DE_VENTANA);
        verDia(token, id, hoy().plusDays(55)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-08 R28: los bloques reflejan el horario vigente al consultar y no se guardan en la base")
    void calculaAlConsultar() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(14);
        ponerHorario(token, id, horario(franja(dia(fecha), 8, 10))).andExpect(status().isOk());
        verDia(token, id, fecha).andExpect(jsonPath("$.bloques.length()").value(2));

        ponerHorario(token, id, horario(franja(dia(fecha), 8, 12))).andExpect(status().isOk());

        verDia(token, id, fecha).andExpect(jsonPath("$.bloques.length()").value(4));
        // reserva_bloque (HU-12) guarda retenciones de reservas, no los bloques calculados.
        var tablas = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = "
                + "'public' AND table_name LIKE '%bloque%'", String.class);
        assertThat(tablas).containsExactlyInAnyOrder("bloqueo_fecha", "reserva_bloque");
    }

    // ── Propiedad y acceso ───────────────────────────────────────────────

    private List<ResultActions> losSeis(String token, long id) throws Exception {
        var fecha = hoy().plusDays(3);
        return List.of(verHorario(token, id), ponerHorario(token, id, lunesAViernes()), verBloqueos(token, id),
                bloquear(token, id, fecha), desbloquear(token, id, fecha), verDia(token, id, fecha));
    }

    @Test
    @DisplayName("HU-08 R32: el espacio de otro arrendador o inexistente responde 404 en los seis y nada cambia")
    void ajenoEsNoEncontrado() throws Exception {
        crearArrendador(CORREO);
        crearArrendador(OTRO_CORREO);
        var tokenAna = token(CORREO);
        var ajeno = crearEspacio(token(OTRO_CORREO));
        insertarBloqueo(ajeno, hoy().plusDays(3));

        for (var respuesta : losSeis(tokenAna, ajeno)) {
            respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }
        for (var respuesta : losSeis(tokenAna, ajeno + 1000)) {
            respuesta.andExpect(status().isNotFound());
        }

        assertThat(filasHorario(ajeno)).isEmpty();
        assertThat(fechasBloqueadas(ajeno)).containsExactly(hoy().plusDays(3));
    }

    @Test
    @DisplayName("HU-08 R33: una cuenta sin rol ARRENDADOR recibe 403 en los seis")
    void otroRolEsAccesoDenegado() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));
        crearUsuario(CORREO_USUARIO);
        var token = token(CORREO_USUARIO);

        for (var respuesta : losSeis(token, id)) {
            respuesta.andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }
        assertThat(filasHorario(id)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R34: sin correo verificado se rechazan las escrituras y se permiten las lecturas")
    void sinCorreoSoloLee() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        quitarCorreoVerificado(ana);
        var fecha = hoy().plusDays(3);

        ponerHorario(token, id, lunesAViernes()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        bloquear(token, id, fecha).andExpect(status().isForbidden());
        desbloquear(token, id, fecha).andExpect(status().isForbidden());
        verHorario(token, id).andExpect(status().isOk());
        verBloqueos(token, id).andExpect(status().isOk());
        verDia(token, id, fecha).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-08 R35: sin sesión los seis responden 401 con el mensaje de sesión inválida")
    void sinSesion() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));

        for (var respuesta : losSeis(null, id)) {
            respuesta.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        }
    }

    @Test
    @DisplayName("HU-08 R36, R7: en BAJA u OCULTO no se cambia horario ni bloqueos; en PAUSADO sí")
    void estadoNoEditable() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(3);
        insertarBloqueo(id, fecha);

        for (var estado : List.of("BAJA", "OCULTO")) {
            forzarEstado(id, estado);
            sinCampo(ponerHorario(token, id, lunesAViernes()), ESTADO_NO_EDITABLE);
            sinCampo(bloquear(token, id, fecha.plusDays(1)), ESTADO_NO_EDITABLE);
            sinCampo(desbloquear(token, id, fecha), ESTADO_NO_EDITABLE);
        }
        assertThat(filasHorario(id)).isEmpty();
        assertThat(fechasBloqueadas(id)).containsExactly(fecha);

        forzarEstado(id, "PAUSADO");
        ponerHorario(token, id, lunesAViernes()).andExpect(status().isOk());
        bloquear(token, id, fecha.plusDays(1)).andExpect(status().isCreated());
        desbloquear(token, id, fecha).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("HU-08 R37: los ids del cuerpo se ignoran y se usa el espacio de la URL")
    void ignoraIdsDelCuerpo() throws Exception {
        crearArrendador(CORREO);
        crearArrendador(OTRO_CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var ajeno = crearEspacio(token(OTRO_CORREO));

        var cuerpo = horario(franja(1, 8, 10));
        cuerpo.put("espacioId", ajeno);
        cuerpo.put("arrendadorId", 999);
        cuerpo.put("id", ajeno);
        ponerHorario(token, id, cuerpo).andExpect(status().isOk());
        var bloqueo = new LinkedHashMap<String, Object>();
        bloqueo.put("fecha", hoy().plusDays(5).toString());
        bloqueo.put("espacioId", ajeno);
        bloqueo.put("usuarioId", 999);
        bloquearCon(token, id, bloqueo).andExpect(status().isCreated());

        assertThat(filasHorario(id)).containsExactly(fila(1, 8, 10));
        assertThat(fechasBloqueadas(id)).containsExactly(hoy().plusDays(5));
        assertThat(filasHorario(ajeno)).isEmpty();
        assertThat(fechasBloqueadas(ajeno)).isEmpty();
    }

    @Test
    @DisplayName("HU-08 R38: un cuerpo mal formado se rechaza por campo antes de comprobar el rol")
    void formaAntesQueRol() throws Exception {
        crearArrendador(CORREO);
        var id = crearEspacio(token(CORREO));
        crearUsuario(CORREO_USUARIO);
        var token = token(CORREO_USUARIO);

        ponerHorario(token, id, horario(franja(1, 8, 30))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("franjas[0].horaFin"));
        campoFecha(bloquear(token, id, "x"), FECHA_INVALIDA);
    }

    @Test
    @DisplayName("HU-08 R39: un espacio ajeno en BAJA responde 404, no estado no editable")
    void propiedadAntesQueEstado() throws Exception {
        crearArrendador(CORREO);
        crearArrendador(OTRO_CORREO);
        var token = token(CORREO);
        var ajeno = crearEspacio(token(OTRO_CORREO));
        forzarEstado(ajeno, "BAJA");

        ponerHorario(token, ajeno, horario(franja(1, 10, 10))).andExpect(status().isNotFound());
        bloquear(token, ajeno, hoy().minusDays(3)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-08 R40: dos bloqueos simultáneos de la misma fecha crean una sola fila")
    void bloqueosSimultaneos() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        var fecha = hoy().plusDays(12);
        var cuerpo = json.writeValueAsString(Map.of("fecha", fecha.toString()));
        var salida = new CountDownLatch(1);
        var hilos = Executors.newFixedThreadPool(2);
        try {
            var tareas = new ArrayList<Future<MockHttpServletResponse>>();
            for (int i = 0; i < 2; i++) {
                var solicitud = con(token, post(ruta(id, "/bloqueos"))).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo);
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(solicitud).andReturn().getResponse();
                }));
            }
            salida.countDown();
            var estados = new ArrayList<Integer>();
            var cuerpos = new ArrayList<String>();
            for (var tarea : tareas) {
                var respuesta = tarea.get();
                estados.add(respuesta.getStatus());
                cuerpos.add(respuesta.getContentAsString());
            }
            assertThat(estados).containsExactlyInAnyOrder(201, 400);
            assertThat(cuerpos.get(estados.indexOf(400))).contains(YA_BLOQUEADA);
        } finally {
            hilos.shutdownNow();
        }

        assertThat(fechasBloqueadas(id)).containsExactly(fecha);
    }

    // ── Auditoría del horario ────────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R41: cambiar el horario se audita con el antes y el después")
    void auditaCambioDeHorario() throws Exception {
        var ana = crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);

        ponerHorario(token, id, horario(franja(2, 8, 22), franja(1, 8, 22))).andExpect(status().isOk());
        ponerHorario(token, id, horario(franja(1, 8, 12))).andExpect(status().isOk());

        var auditoria = auditorias(HORARIO);
        assertThat(auditoria).hasSize(2);
        assertThat(auditoria.get(0)).containsEntry("usuario_id", ana.getId()).containsEntry("entidad", "espacio")
                .containsEntry("entidad_id", id).containsEntry("ip", ip)
                .containsEntry("detalle", "antes=ninguna;despues=1:8-22,2:8-22");
        assertThat(instante(auditoria.get(0).get("fecha"))).isEqualTo(reloj.instant());
        assertThat(auditoria.get(1)).containsEntry("detalle", "antes=1:8-22,2:8-22;despues=1:8-12");
    }

    @Test
    @DisplayName("HU-08 R42: el mismo horario en otro orden no se vuelve a auditar")
    void mismoHorarioNoAudita() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, horario(franja(2, 8, 22), franja(1, 8, 22))).andExpect(status().isOk());

        ponerHorario(token, id, horario(franja(1, 8, 22), franja(2, 8, 22))).andExpect(status().isOk());

        assertThat(auditorias(HORARIO)).hasSize(1);
    }

    // ── Minimización ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-08 R45: horario y bloqueos solo exponen sus campos, sin ids ni arrendador")
    void minimizaRespuestas() throws Exception {
        crearArrendador(CORREO);
        var token = token(CORREO);
        var id = crearEspacio(token);
        ponerHorario(token, id, horario(franja(1, 8, 10))).andExpect(status().isOk());
        var creado = mapa(cuerpo(bloquear(token, id, hoy().plusDays(2)).andExpect(status().isCreated())));

        var horario = mapa(cuerpo(verHorario(token, id)));
        var bloqueos = mapa(cuerpo(verBloqueos(token, id)));

        assertThat(creado).containsOnlyKeys("fecha");
        assertThat(horario).containsOnlyKeys("franjas");
        @SuppressWarnings("unchecked")
        var franjas = (List<Map<String, Object>>) horario.get("franjas");
        assertThat(franjas.get(0)).containsOnlyKeys("diaSemana", "horaInicio", "horaFin");
        assertThat(bloqueos).containsOnlyKeys("desde", "hasta", "fechas");
    }
}
