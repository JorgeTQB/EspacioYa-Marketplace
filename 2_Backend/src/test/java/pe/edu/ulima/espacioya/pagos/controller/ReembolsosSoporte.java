package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Base de las IT de HU-16: reservas pagadas por la API con la pasarela simulada, canceladas por la API, y reembolsos
 * sembrados en la base. Cada test usa su propia IP (10.18.x.x); el admin entra desde {@link #IP_ADMIN}.
 */
abstract class ReembolsosSoporte extends PagosSoporte {

    static final String CORREO_ADMIN = "admin.hu16@espacioya.pe";
    static final String ADMIN_REEMBOLSOS = "/api/admin/reembolsos";
    static final String MONTO_PAGADO = "241.50";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_SEMBRADO = new AtomicInteger(1);

    @BeforeEach
    void prepararHu16() {
        // Corre después del @BeforeEach del soporte: reemplaza su IP 10.15 por una de HU-16.
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.18." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    /** Una reserva PENDIENTE_PAGO vigente del usuario, de S/ 241.50 (3 bloques), en {@code dia} desde {@code hora}. */
    long vigenteEl(LocalDate dia, int hora) {
        var expiraEn = dentroDe(10);
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'PENDIENTE_PAGO', 80.50, 241.50, ?, ?) "
                        + "RETURNING id", Long.class, usuarioId, espacioId,
                Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))), Timestamp.from(expiraEn));
        for (var h = hora; h < hora + 3; h++) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, true)", id, espacioId, Date.valueOf(dia), h);
        }
        return id;
    }

    /** Paga la reserva por la API con la pasarela simulada (APROBADO): queda CONFIRMADA con su pago PAGADO. */
    void pagarPorLaApi(String sesion, long reserva) throws Exception {
        pagar(sesion, reserva).andExpect(status().isOk());
        mvc.perform(con(sesion, post("/api/reservas/" + reserva + "/pago/simulacion"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isNoContent());
        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    /** Una reserva a 7 días (100 % de reembolso para el usuario), pagada por la API. */
    long pagadaPorLaApi(String sesion) throws Exception {
        var reserva = vigente();
        pagarPorLaApi(sesion, reserva);
        return reserva;
    }

    ResultActions cancelar(String sesion, long reserva, String montoVisto) throws Exception {
        return mvc.perform(con(sesion, post("/api/reservas/" + reserva + "/cancelacion"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"montoReembolsoVisto\":" + montoVisto + "}"));
    }

    /**
     * Un reembolso sembrado sobre un pago propio (reserva CANCELADA sin bloques), con el pago en {@code estadoPago} y
     * id de transacción {@code sim-…}. Devuelve el id del reembolso.
     */
    long reembolsoSembrado(String estado, int intentos, Instant creadoEn, Instant ultimoIntentoEn,
                           String estadoPago) {
        var pago = pagoSembrado(estadoPago);
        var idPasarela = "REEMBOLSADO".equals(estado) ? "sim-reembolso-sembrado-" + pago : null;
        return jdbc.queryForObject("INSERT INTO reembolso (pago_id, monto, origen, estado, intentos, creado_en, "
                        + "ultimo_intento_en, id_reembolso_pasarela) VALUES (?, 241.50, 'CANCELACION', ?, ?, ?, ?, ?) "
                        + "RETURNING id", Long.class, pago, estado, intentos, Timestamp.from(creadoEn),
                ultimoIntentoEn == null ? null : Timestamp.from(ultimoIntentoEn), idPasarela);
    }

    /** Un pago de S/ 241.50 en {@code estadoPago}, id {@code sim-…}, de una reserva CANCELADA propia sin bloques. */
    long pagoSembrado(String estadoPago) {
        var reserva = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CANCELADA', 80.50, 241.50, ?, ?) "
                        + "RETURNING id", Long.class, usuarioId, espacioId, Timestamp.from(haceMinutos(120)),
                Timestamp.from(haceMinutos(105)));
        return jdbc.queryForObject("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, "
                        + "creado_en) VALUES (?, ?, 241.50, ?, '4242', ?) RETURNING id", Long.class, reserva,
                "sim-" + reserva + "-APROBADO-" + SIGUIENTE_SEMBRADO.getAndIncrement(), estadoPago,
                Timestamp.from(haceMinutos(110)));
    }

    /** Un FALLIDO de un intento de hace 10 min, con el pago en REEMBOLSO_FALLIDO. */
    long fallidoSembrado() {
        return reembolsoSembrado("FALLIDO", 1, haceMinutos(10), haceMinutos(10), "REEMBOLSO_FALLIDO");
    }

    // ── Admin ────────────────────────────────────────────────────────────

    String tokenAdmin() throws Exception {
        crearAdmin(CORREO_ADMIN);
        return token(CORREO_ADMIN, IP_ADMIN);
    }

    /** La solicitud con la sesión dada, desde la IP permitida del admin. */
    <T extends AbstractMockHttpServletRequestBuilder<T>> T comoAdmin(String sesion, T solicitud) {
        if (sesion != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + sesion);
        }
        return desde(solicitud, IP_ADMIN);
    }

    static String rutaReintento(Object id) {
        return ADMIN_REEMBOLSOS + "/" + id + "/reintento";
    }

    // ── Consultas ────────────────────────────────────────────────────────

    Map<String, Object> reembolso(long id) {
        return jdbc.queryForMap("SELECT * FROM reembolso WHERE id = ?", id);
    }

    List<Map<String, Object>> reembolsosDe(long reserva) {
        return jdbc.queryForList("SELECT r.* FROM reembolso r JOIN pago p ON p.id = r.pago_id WHERE p.reserva_id = ? "
                + "ORDER BY r.id", reserva);
    }

    Map<String, Object> pagoDe(long reserva) {
        var pagos = pagosDe(reserva);
        assertThat(pagos).hasSize(1);
        return pagos.getFirst();
    }

    String estadoDelPagoDelReembolso(long reembolso) {
        return jdbc.queryForObject("SELECT p.estado FROM pago p JOIN reembolso r ON r.pago_id = p.id WHERE r.id = ?",
                String.class, reembolso);
    }

    static long numero(Object valor) {
        return ((Number) valor).longValue();
    }

    static Instant instante(Object valor) {
        return valor instanceof Timestamp marca ? marca.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    static BigDecimal decimal(Object valor) {
        return new BigDecimal(valor.toString());
    }
}
