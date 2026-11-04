package pe.edu.ulima.espacioya.pagos.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import tools.jackson.core.type.TypeReference;

/**
 * Base de las IT de HU-19: reservas pagadas por la API con la pasarela simulada (filas {@code sim-}), reloj llevado al
 * fin + 48 h, cuentas bancarias sembradas y llamadas a las rutas de liquidación. Cada test usa su propia IP
 * (10.19.x.x); el admin entra desde {@link #IP_ADMIN}.
 */
abstract class LiquidacionesSoporte extends ReembolsosSoporte {

    static final String ADMIN_LIQUIDACIONES = "/api/admin/liquidaciones";
    static final String MIS_LIQUIDACIONES = "/api/arrendadores/actual/liquidaciones";
    static final String BANCO = "Banco Andino";
    static final String NUMERO_CUENTA = "00112233445566771234";
    static final String OTRO_CORREO_ARRENDADOR = "otro.arrendador.hu19@correo.pe";
    static final Duration ESPERA = Duration.ofHours(48);

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_HUELLA = new AtomicInteger(500);
    private static final AtomicInteger SIGUIENTE_TRANSACCION = new AtomicInteger(1);

    @BeforeEach
    void prepararHu19() {
        // Corre después de los @BeforeEach de los soportes: reemplaza su IP por una de HU-19.
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.19." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    /** Día de las reservas de prueba: dentro de 7 días en Lima. */
    LocalDate dia() {
        return ZonaHoraria.hoy(reloj).plusDays(7);
    }

    /** Una reserva de 3 bloques desde {@code hora}, pagada por la API con la simulada: CONFIRMADA y pago PAGADO. */
    long pagadaEl(String sesion, LocalDate dia, int hora) throws Exception {
        var reserva = vigenteEl(dia, hora);
        pagarPorLaApi(sesion, reserva);
        return reserva;
    }

    /** Fin (último bloque + 1 h) en Lima de una reserva de 3 bloques que empieza a {@code hora}. */
    static Instant finDe(LocalDate dia, int hora) {
        return dia.atTime(hora + 3, 0).atZone(ZonaHoraria.LIMA).toInstant();
    }

    /** Lleva el reloj compartido exactamente a {@code instante} (solo avanza). */
    void llevarRelojA(Instant instante) {
        reloj.avanzar(Duration.between(reloj.instant(), instante));
    }

    long otroArrendador() {
        var usuario = usuarios.save(Usuario.registrarArrendador(OTRO_CORREO_ARRENDADOR,
                encoder.encode(CONTRASENA), "Beto Arrendador", "999333444", "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070971",
                String.format("%064d", SIGUIENTE_HUELLA.getAndIncrement()), "Beto SAC", "Jr. Otro 1"));
        return usuario.getId();
    }

    long espacioDe(long arrendador) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Auditorio Beto', "
                        + "'AUDITORIO', 'Av. Arequipa 100', 'LOS_OLIVOS', 50, 100.00, 'Auditorio techado.', "
                        + "'PUBLICADO', now(), now()) RETURNING id", Long.class, arrendador);
    }

    /** Cuenta bancaria vigente sembrada, cifrada como la guarda HU-18; {@code retenerHasta} puede ser nulo. */
    void cuentaDe(long arrendador, String numero, Instant retenerHasta) {
        jdbc.update("INSERT INTO cuenta_bancaria (arrendador_id, banco, numero, titular, retener_hasta, creada_en, "
                        + "actualizada_en) VALUES (?, ?, ?, 'Inversiones Ocultas SAC', ?, ?, ?)", arrendador, BANCO,
                cifrador.cifrar(numero), retenerHasta == null ? null : Timestamp.from(retenerHasta),
                Timestamp.from(reloj.instant().minus(Duration.ofDays(30))), Timestamp.from(reloj.instant()));
    }

    void verificarIdentidad(long arrendador) {
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = 'VERIFICADA' WHERE usuario_id = ?", arrendador);
    }

    /** Reserva sembrada sin bloques en el estado dado, de S/ 241.50, en el espacio del arrendador principal. */
    long reservaSinBloques(String estado) {
        return jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, 241.50, ?, ?) RETURNING id",
                Long.class, usuarioId, espacioId, estado, Timestamp.from(haceMinutos(120)),
                Timestamp.from(haceMinutos(105)));
    }

    /** Un pago sembrado de S/ 241.50; devuelve su id. */
    long pagoSembrado(long reserva, String idTransaccion, String estado) {
        return jdbc.queryForObject("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, "
                        + "creado_en) VALUES (?, ?, 241.50, ?, '4242', ?) RETURNING id", Long.class, reserva,
                idTransaccion, estado, Timestamp.from(haceMinutos(110)));
    }

    static String transaccionReal() {
        return "MP-" + SIGUIENTE_TRANSACCION.getAndIncrement();
    }

    static String transaccionSimulada() {
        return "sim-1-APROBADO-" + SIGUIENTE_TRANSACCION.getAndIncrement();
    }

    /** Liquidación por pagar sembrada con montos coherentes con los CHECK; devuelve su id. */
    long liquidacionSembrada(long arrendador, String estado, String bruto, Instant generadaEn, long adminId) {
        var monto = new BigDecimal(bruto);
        var comision = monto.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        return jdbc.queryForObject("INSERT INTO liquidacion (arrendador_id, periodo, monto_bruto, comision, "
                        + "monto_neto, estado, simulada, generada_en, generada_por) "
                        + "VALUES (?, ?, ?, ?, ?, ?, false, ?, ?) RETURNING id", Long.class, arrendador,
                Date.valueOf(LocalDate.ofInstant(generadaEn, ZonaHoraria.LIMA)), monto, comision,
                monto.subtract(comision), estado, Timestamp.from(generadaEn), adminId);
    }

    /** Liquidación PAGADA sembrada (destino {@code BANCO} / {@code ****1234}); devuelve su id. */
    long pagadaSembrada(long arrendador, Instant generadaEn, Instant pagadaEn, long adminId, String operacion) {
        var id = liquidacionSembrada(arrendador, "GENERADA", "100.00", generadaEn, adminId);
        jdbc.update("UPDATE liquidacion SET estado = 'PAGADA', numero_operacion = ?, pagada_en = ?, pagada_por = ?, "
                + "banco_destino = ?, cuenta_destino = '****1234' WHERE id = ?", operacion, Timestamp.from(pagadaEn),
                adminId, BANCO, id);
        return id;
    }

    /**
     * Una reserva CONFIRMADA ya usada sembrada en la base: 3 bloques desde {@code hora} en {@code dia} y su pago
     * PAGADO de S/ 241.50 con {@code idTransaccion}. Devuelve el id de la reserva.
     */
    long usadaSembrada(long espacio, LocalDate dia, int hora, String idTransaccion) {
        var reserva = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 80.50, 241.50, ?, ?) "
                        + "RETURNING id", Long.class, usuarioId, espacio, Timestamp.from(haceMinutos(60 * 24 * 20)),
                Timestamp.from(haceMinutos(60 * 24 * 20 - 15)));
        for (var h = hora; h < hora + 3; h++) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, true)", reserva, espacio, Date.valueOf(dia), h);
        }
        pagoSembrado(reserva, idTransaccion, "PAGADO");
        return reserva;
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    ResultActions generar(String sesion) throws Exception {
        return mvc.perform(comoAdmin(sesion, post(ADMIN_LIQUIDACIONES)));
    }

    ResultActions listarAdmin(String sesion, String parametros) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(ADMIN_LIQUIDACIONES + parametros)));
    }

    ResultActions detalle(String sesion, Object id) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(ADMIN_LIQUIDACIONES + "/" + id)));
    }

    ResultActions marcarPagada(String sesion, Object id, String cuerpo) throws Exception {
        return mvc.perform(comoAdmin(sesion, post(ADMIN_LIQUIDACIONES + "/" + id + "/pago"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    ResultActions misLiquidaciones(String sesion, String parametros) throws Exception {
        return mvc.perform(con(sesion, get(MIS_LIQUIDACIONES + parametros)));
    }

    static String operacion(String numero) {
        return "{\"numeroOperacion\":\"" + numero + "\"}";
    }

    long idAdmin() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", Long.class, CORREO_ADMIN);
    }

    // ── Consultas ────────────────────────────────────────────────────────

    List<Map<String, Object>> liquidaciones() {
        return jdbc.queryForList("SELECT * FROM liquidacion ORDER BY id");
    }

    Map<String, Object> liquidacion(long id) {
        return jdbc.queryForMap("SELECT * FROM liquidacion WHERE id = ?", id);
    }

    List<Map<String, Object>> lineasDe(long liquidacion) {
        return jdbc.queryForList("SELECT * FROM liquidacion_detalle WHERE liquidacion_id = ? ORDER BY reserva_id",
                liquidacion);
    }

    Map<String, Object> cuerpo(ResultActions respuesta) throws Exception {
        return json.readValue(texto(respuesta), new TypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> lista(Map<String, Object> cuerpo, String clave) {
        return (List<Map<String, Object>>) cuerpo.get(clave);
    }
}
