package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import tools.jackson.core.type.TypeReference;

/**
 * HU-21 CA2 de punta a punta con el puerto real de reservas: la baja ve las reservas por terminar, el arrendador
 * las cancela con HU-15 (100 %) y la baja siguiente pasa. Las reservas se siembran en la base con su pago PAGADO; las
 * fechas se calculan desde el reloj, que solo avanza. Cada test usa su IP (10.21.200.x).
 */
class BajaEspacioConReservasIT extends ReservasControllerSoporte {

    private static final String ESPACIOS = "/api/arrendadores/actual/espacios/";
    private static final String DE_MIS_ESPACIOS = "/api/arrendadores/actual/reservas";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String RESERVAS_POR_TERMINAR = "Este espacio tiene reservas por terminar. Cancela sus "
            + "reservas futuras (con reembolso del 100 %) y vuelve a intentarlo. Si hay una en curso o pendiente de "
            + "pago, espera a que termine o venza.";
    private static final String CORREO_OTRO_ARRENDADOR = "luis.hu21@correo.pe";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_PAGO = new AtomicInteger(1);

    private String arrendador;

    @BeforeEach
    void prepararHu21() throws Exception {
        // Corre después del @BeforeEach del soporte: reemplaza su IP 10.14 por una de HU-21.
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.21." + (200 + n / 250) + "." + (n % 250 + 1);
        arrendador = token(CORREO_ARRENDADOR);
    }

    // pago y reembolso no tienen cascada desde reserva; este @AfterEach corre antes que el limpiar() del soporte.
    @AfterEach
    void borrarPagos() {
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM pago");
    }

    // ── Datos ────────────────────────────────────────────────────────────

    /** Una reserva sembrada en {@code dia} con un bloque por hora; {@code activo} = los bloques retienen el horario. */
    private long sembrar(String estado, Instant expiraEn, boolean activo, LocalDate dia, int... horas) {
        var monto = new BigDecimal("80.50").multiply(BigDecimal.valueOf(horas.length));
        var reserva = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, ?, ?, ?) RETURNING id",
                Long.class, usuarioId, espacioId, estado, monto,
                Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))), Timestamp.from(expiraEn));
        for (var hora : horas) {
            filaBloque(reserva, espacioId, dia, hora, activo);
        }
        return reserva;
    }

    /** CONFIRMADA de 2 horas (S/ 161.00) con su pago PAGADO. */
    private long pagada(LocalDate dia, int horaInicio) {
        var reserva = sembrar("CONFIRMADA", haceMinutos(45), true, dia, horaInicio, horaInicio + 1);
        jdbc.update("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, creado_en) "
                        + "VALUES (?, ?, 161.00, 'PAGADO', '1234', ?)", reserva,
                "sim-hu21-" + SIGUIENTE_PAGO.getAndIncrement(), Timestamp.from(haceMinutos(50)));
        return reserva;
    }

    private long otroEspacioAjeno() {
        var otro = usuarios.save(Usuario.registrarArrendador(CORREO_OTRO_ARRENDADOR, encoder.encode(CONTRASENA),
                "Luis Otro", "988777666", "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(otro.getId(), TipoDocumento.RUC, "20100071000",
                String.format("h21%061d", otro.getId()), "Otra Empresa SAC", "Jr. Otro 123"));
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Losa Ajena', 'CANCHA', "
                + "'Av. Otra 99, Los Olivos', 'LOS_OLIVOS', 10, 80.50, 'Losa de concreto.', 'PUBLICADO', now(), now()) "
                + "RETURNING id", Long.class, otro.getId());
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private ResultActions pausar() throws Exception {
        return mvc.perform(con(arrendador, post(ESPACIOS + espacioId + "/pausa")));
    }

    private ResultActions darDeBaja() throws Exception {
        return mvc.perform(con(arrendador, post(ESPACIOS + espacioId + "/baja")));
    }

    private ResultActions afectadas(Object espacio) throws Exception {
        return mvc.perform(con(arrendador, get(DE_MIS_ESPACIOS + "?grupo=proximas&pagina=1&espacioId=" + espacio)));
    }

    private List<Long> idsAfectadas(Object espacio) throws Exception {
        var cuerpo = afectadas(espacio).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> pagina = json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
        @SuppressWarnings("unchecked")
        var reservas = (List<Map<String, Object>>) pagina.get("reservas");
        return reservas.stream().map(fila -> ((Number) fila.get("id")).longValue()).toList();
    }

    private static void rechazadaPorReservas(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(RESERVAS_POR_TERMINAR));
    }

    private String estadoDelEspacio() {
        return jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, espacioId);
    }

    // ── Flujo principal (CA2) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-21 R13, R19, R23, R24: baja rechazada por una CONFIRMADA futura, se cancela al 100 % y la baja "
            + "pasa")
    void bajaConReservaFuturaCanceladaAlCien() throws Exception {
        var reserva = pagada(fecha, 18);
        pausar().andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAUSADO"));

        rechazadaPorReservas(darDeBaja());
        assertThat(estadoDelEspacio()).isEqualTo("PAUSADO");
        assertThat(auditorias("BAJA_ESPACIO")).isEmpty();

        assertThat(idsAfectadas(espacioId)).containsExactly(reserva);
        mvc.perform(con(arrendador, get(DE_MIS_ESPACIOS + "/" + reserva + "/cancelacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.politica").value("ARRENDADOR"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(161.0));
        mvc.perform(con(arrendador, post(DE_MIS_ESPACIOS + "/" + reserva + "/cancelacion"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montoReembolsoVisto\":161.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.canceladaPor").value("ARRENDADOR"))
                .andExpect(jsonPath("$.politica").value("ARRENDADOR"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(161.0));
        assertThat(idsAfectadas(espacioId)).isEmpty();

        darDeBaja().andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BAJA"));

        assertThat(estadoDelEspacio()).isEqualTo("BAJA");
        assertThat(estadoGuardado(reserva)).isEqualTo("CANCELADA");
        var bajas = auditorias("BAJA_ESPACIO");
        assertThat(bajas).hasSize(1);
        assertThat(bajas.getFirst().get("detalle")).isEqualTo("estado=PAUSADO->BAJA");
    }

    @Test
    @DisplayName("HU-21 R5: pausar conserva el estado y los bloques activos de las CONFIRMADA y PENDIENTE_PAGO")
    void pausaConservaReservas() throws Exception {
        var confirmada = pagada(fecha, 18);
        var pendiente = sembrar("PENDIENTE_PAGO", dentroDe(10), true, fecha, 10);
        var bloquesConfirmada = bloquesGuardados(confirmada);
        var bloquesPendiente = bloquesGuardados(pendiente);

        pausar().andExpect(status().isOk());

        assertThat(estadoGuardado(confirmada)).isEqualTo("CONFIRMADA");
        assertThat(estadoGuardado(pendiente)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesGuardados(confirmada)).isEqualTo(bloquesConfirmada);
        assertThat(bloquesGuardados(pendiente)).isEqualTo(bloquesPendiente);
        assertThat(bloquesActivos(espacioId)).isEqualTo(3);
    }

    // ── Qué reservas impiden la baja (R13–R15) ───────────────────────────

    @Test
    @DisplayName("HU-21 R14: una CONFIRMADA en curso impide la baja")
    void reservaEnCursoImpideLaBaja() throws Exception {
        var ahora = ZonaHoraria.ahora(reloj);
        sembrar("CONFIRMADA", haceMinutos(45), true, ahora.toLocalDate(), ahora.getHour());

        rechazadaPorReservas(darDeBaja());

        assertThat(estadoDelEspacio()).isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("HU-21 R14: una PENDIENTE_PAGO vigente impide la baja")
    void pendienteVigenteImpideLaBaja() throws Exception {
        sembrar("PENDIENTE_PAGO", dentroDe(10), true, fecha, 9);

        rechazadaPorReservas(darDeBaja());

        assertThat(estadoDelEspacio()).isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("HU-21 R15: PENDIENTE_PAGO vencida, EXPIRADA, CANCELADA y CONFIRMADA terminada no impiden la baja")
    void reservasQueNoImpidenLaBaja() throws Exception {
        sembrar("PENDIENTE_PAGO", haceMinutos(5), true, fecha, 9);
        sembrar("EXPIRADA", haceMinutos(30), false, fecha, 10);
        sembrar("CANCELADA", haceMinutos(60), false, fecha, 11);
        var haceUnaHora = ZonaHoraria.ahora(reloj).minusHours(1);
        sembrar("CONFIRMADA", haceMinutos(120), true, haceUnaHora.toLocalDate(), haceUnaHora.getHour());

        darDeBaja().andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BAJA"));

        assertThat(estadoDelEspacio()).isEqualTo("BAJA");
    }

    @Test
    @DisplayName("HU-21 R13: una CONFIRMADA futura impide la baja de un PUBLICADO y no lo cambia")
    void confirmadaFuturaImpideLaBajaDePublicado() throws Exception {
        pagada(ZonaHoraria.hoy(reloj).plusDays(1), 20);
        var antes = jdbc.queryForMap("SELECT * FROM espacio WHERE id = ?", espacioId);

        rechazadaPorReservas(darDeBaja());

        assertThat(jdbc.queryForMap("SELECT * FROM espacio WHERE id = ?", espacioId)).isEqualTo(antes);
    }

    // ── Reservas afectadas (R19–R21) ─────────────────────────────────────

    @Test
    @DisplayName("HU-21 R19: el filtro espacioId devuelve solo las CONFIRMADA de ese espacio")
    void filtroSoloDeEseEspacio() throws Exception {
        var propia = pagada(fecha, 18);
        var otroPropio = publicado("90.00");
        var deOtroPropio = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, "
                        + "precio_hora_aplicado, monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 90.00, "
                        + "90.00, ?, ?) RETURNING id", Long.class, usuarioId, otroPropio,
                Timestamp.from(haceMinutos(60)), Timestamp.from(haceMinutos(45)));
        filaBloque(deOtroPropio, otroPropio, fecha, 12, true);
        sembrar("PENDIENTE_PAGO", dentroDe(10), true, fecha, 9);

        assertThat(idsAfectadas(espacioId)).containsExactly(propia);
        assertThat(idsAfectadas(otroPropio)).containsExactly(deOtroPropio);
    }

    @Test
    @DisplayName("HU-21 R20, R21: espacioId ajeno o inexistente da 404 y uno mal escrito da 400 en espacioId")
    void filtroAjenoOInvalido() throws Exception {
        var ajeno = otroEspacioAjeno();

        afectadas(ajeno).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        afectadas(ajeno + 1000).andExpect(status().isNotFound());
        afectadas("abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("espacioId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El espacio no es válido."));
    }
}
