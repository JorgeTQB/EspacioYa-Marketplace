package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;

/**
 * Cancelación de reservas contra PostgreSQL real (HU-15). Las reservas CONFIRMADA se siembran con su pago PAGADO; un
 * caso de punta a punta llega a CONFIRMADA por la API con la pasarela simulada. Los correos los guarda un doble de
 * {@link NotificadorCorreo}. El reloj solo avanza: las fechas se calculan desde él. Cada test usa su IP (10.17.x.x).
 */
class CancelacionReservaControllerIT extends ReservasControllerSoporte {

    private static final String DE_MIS_ESPACIOS = "/api/arrendadores/actual/reservas";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String INESPERADO = "Ocurrió un problema. Intenta de nuevo más tarde.";
    private static final String YA_CANCELADA = "Esta reserva ya está cancelada.";
    private static final String NO_CONFIRMADA = "Solo se puede cancelar una reserva confirmada.";
    private static final String YA_INICIADA = "Esta reserva ya empezó o terminó: no se puede cancelar.";
    private static final String MONTO_VISTO = "Indica el monto a reembolsar que se te mostró.";
    private static final String MONTO_CAMBIO = "El monto a reembolsar cambió. Revísalo antes de confirmar.";
    private static final String CAMPO = "montoReembolsoVisto";
    private static final String NOMBRE = "Cancha Los Olivos";
    private static final String CORREO_ADMIN = "admin.hu15@espacioya.pe";
    private static final String CORREO_OTRO_ARRENDADOR = "luis.hu15@correo.pe";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_HUELLA = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_PAGO = new AtomicInteger(1);
    private static String hash;

    @Autowired
    private NotificadorCancelaciones notificador;

    private LocalDate hoy;

    record AvisoAlUsuario(String correo, AvisoCancelacionUsuario datos) {
    }

    record AvisoAlArrendador(String correo, AvisoCancelacionArrendador datos) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Correos {

        @Bean
        @Primary
        NotificadorCancelaciones notificadorCancelaciones() {
            return new NotificadorCancelaciones();
        }
    }

    /** Guarda los avisos de cancelación; los demás correos no importan aquí. */
    static class NotificadorCancelaciones implements NotificadorCorreo {

        private final List<AvisoAlUsuario> alUsuario = new CopyOnWriteArrayList<>();
        private final List<AvisoAlArrendador> alArrendador = new CopyOnWriteArrayList<>();
        private volatile boolean fallaAlUsuario;

        @Override
        public void notificarVerificacion(String correoNormalizado) {
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
        }

        @Override
        public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
        }

        @Override
        public void notificarCuentaReactivada(String correoNormalizado) {
        }

        @Override
        public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
        }

        @Override
        public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
        }

        @Override
        public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
            if (fallaAlUsuario) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            alUsuario.add(new AvisoAlUsuario(correoUsuario, aviso));
        }

        @Override
        public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
            alArrendador.add(new AvisoAlArrendador(correoArrendador, aviso));
        }

        @Override

        public void notificarCambioCorreo(String correoNuevo, String enlace) {

        }


        @Override

        public void notificarAvisoCambioCorreo(String correoActual, String correoNuevoEnmascarado) {

        }


        @Override
        public void notificarCambioCuentaBancaria(String correoArrendador,
                pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria aviso) {
        }

        void fallarAlUsuario() {
            fallaAlUsuario = true;
        }

        void reiniciar() {
            alUsuario.clear();
            alArrendador.clear();
            fallaAlUsuario = false;
        }
    }

    @BeforeEach
    void prepararHu15() {
        // Corre después del @BeforeEach del soporte: reemplaza su IP 10.14 por una de HU-15.
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.17." + (n / 250) + "." + (n % 250 + 1);
        hoy = ZonaHoraria.hoy(reloj);
        notificador.reiniciar();
        if (hash == null) {
            hash = encoder.encode(CONTRASENA);
        }
    }

    // pago no tiene cascada desde reserva y el soporte no lo borra; este @AfterEach corre antes que su limpiar().
    @AfterEach
    void borrarPagos() {
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM pago");
    }

    // ── Datos ────────────────────────────────────────────────────────────

    /** Una reserva sembrada en {@code dia}, de S/ 80.50 por hora, con un bloque activo por hora. */
    private long sembrar(long usuario, long espacio, String estado, LocalDate dia, int... horas) {
        var expiraEn = estado.equals("PENDIENTE_PAGO") ? dentroDe(10) : haceMinutos(45);
        var monto = new BigDecimal("80.50").multiply(BigDecimal.valueOf(horas.length));
        var reserva = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, ?, ?, ?) RETURNING id",
                Long.class, usuario, espacio, estado, monto, Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))),
                Timestamp.from(expiraEn));
        for (var hora : horas) {
            filaBloque(reserva, espacio, dia, hora, !estado.equals("EXPIRADA"));
        }
        return reserva;
    }

    private void filaPagoPagado(long reserva, String monto) {
        jdbc.update("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, creado_en) "
                        + "VALUES (?, ?, ?, 'PAGADO', '1234', ?)", reserva,
                "sim-hu15-" + SIGUIENTE_PAGO.getAndIncrement(), new BigDecimal(monto),
                Timestamp.from(haceMinutos(50)));
    }

    /** CONFIRMADA del usuario en el espacio de la clase, de 2 horas (S/ 161.00), con su pago PAGADO. */
    private long pagada(LocalDate dia, int horaInicio) {
        var reserva = sembrar(usuarioId, espacioId, "CONFIRMADA", dia, horaInicio, horaInicio + 1);
        filaPagoPagado(reserva, "161.00");
        return reserva;
    }

    /** Más de 48 h: el día con franja de la clase (hoy + 7), de 18:00 a 20:00. */
    private long pagadaLejos() {
        return pagada(fecha, 18);
    }

    /** Menos de 48 h: mañana de 22:00 a 24:00 (como mucho 47 h desde las 00:00 de hoy). */
    private long pagadaCerca() {
        return pagada(hoy.plusDays(1), 22);
    }

    private long otroArrendador() {
        var cuenta = usuarios.save(Usuario.registrarArrendador(CORREO_OTRO_ARRENDADOR, hash, "Luis Otro", "988777666",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(cuenta.getId(), TipoDocumento.RUC, "2010007" + (1000 + cuenta.getId()),
                String.format("c%063d", SIGUIENTE_HUELLA.getAndIncrement()), "Otra Empresa SAC", "Jr. Otro 123"));
        return cuenta.getId();
    }

    private long espacioDe(long arrendador) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Losa Ajena', "
                        + "'CANCHA', 'Av. Otra 99, Los Olivos', 'LOS_OLIVOS', 10, 80.50, 'Losa de concreto.', "
                        + "'PUBLICADO', now(), now()) RETURNING id", Long.class, arrendador);
    }

    private String tokenAdmin() throws Exception {
        crearAdmin(CORREO_ADMIN);
        return token(CORREO_ADMIN, IP_ADMIN);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private static String rutaUsuario(Object id) {
        return RESERVAS + "/" + id + "/cancelacion";
    }

    private static String rutaArrendador(Object id) {
        return DE_MIS_ESPACIOS + "/" + id + "/cancelacion";
    }

    private ResultActions verRuta(String sesion, String ruta) throws Exception {
        return mvc.perform(con(sesion, get(ruta)));
    }

    private ResultActions confirmarRuta(String sesion, String ruta, String cuerpo) throws Exception {
        var solicitud = con(sesion, post(ruta));
        if (cuerpo != null) {
            solicitud.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(solicitud);
    }

    private ResultActions vista(String sesion, Object id) throws Exception {
        return verRuta(sesion, rutaUsuario(id));
    }

    private ResultActions cancelar(String sesion, Object id, String monto) throws Exception {
        return confirmarRuta(sesion, rutaUsuario(id), visto(monto));
    }

    private ResultActions vistaDelArrendador(String sesion, Object id) throws Exception {
        return verRuta(sesion, rutaArrendador(id));
    }

    private ResultActions cancelarDelArrendador(String sesion, Object id, String monto) throws Exception {
        return confirmarRuta(sesion, rutaArrendador(id), visto(monto));
    }

    private static String visto(String monto) {
        return "{\"montoReembolsoVisto\":" + monto + "}";
    }

    private void esErrorDeMonto(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(CAMPO))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private static void esError(ResultActions respuesta, int codigo, String mensaje) throws Exception {
        respuesta.andExpect(status().is(codigo)).andExpect(jsonPath("$.mensaje").value(mensaje));
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private record Foto(Map<String, Object> reserva, List<Map<String, Object>> bloques, int auditorias,
                        List<Map<String, Object>> pagos) {
    }

    private Foto foto(long reserva) {
        return new Foto(reservaGuardada(reserva), bloquesGuardados(reserva), filas("auditoria"),
                jdbc.queryForList("SELECT * FROM pago ORDER BY id"));
    }

    /** La reserva, sus bloques, la auditoría y los pagos siguen como estaban, y no salió ningún correo (R36, R29). */
    private void sinCambios(long reserva, Foto antes) {
        assertThat(foto(reserva)).isEqualTo(antes);
        assertThat(auditorias("RESERVA_CANCELADA")).isEmpty();
        assertThat(auditorias("REEMBOLSO_SOLICITADO")).isEmpty();
        assertThat(notificador.alUsuario).isEmpty();
        assertThat(notificador.alArrendador).isEmpty();
    }

    private long pagoDe(long reserva) {
        return jdbc.queryForObject("SELECT id FROM pago WHERE reserva_id = ? AND estado = 'PAGADO'", Long.class,
                reserva);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listado(ResultActions respuesta) throws Exception {
        return (List<Map<String, Object>>) mapa(texto(respuesta.andExpect(status().isOk()))).get("reservas");
    }

    private static List<Long> ids(List<Map<String, Object>> reservas) {
        return reservas.stream().map(reserva -> ((Number) reserva.get("id")).longValue()).toList();
    }

    // ── Sesión, rol y propiedad (R1–R7) ──────────────────────────────────

    @Test
    @DisplayName("HU-15 R1: las cuatro rutas sin sesión responden 401")
    void sinSesion() throws Exception {
        var reserva = pagadaLejos();

        esError(vista(null, reserva), 401, SESION_INVALIDA);
        esError(cancelar(null, reserva, "161.00"), 401, SESION_INVALIDA);
        esError(vistaDelArrendador(null, reserva), 401, SESION_INVALIDA);
        esError(cancelarDelArrendador(null, reserva, "161.00"), 401, SESION_INVALIDA);
        assertThat(estadoGuardado(reserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("HU-15 R2, R36: ARRENDADOR y ADMIN en la ruta del usuario reciben 403, con o sin cuerpo, sin cambios")
    void rolAjenoEnLaRutaDelUsuario() throws Exception {
        var reserva = pagadaLejos();
        var sesiones = List.of(token(CORREO_ARRENDADOR), tokenAdmin());
        var antes = foto(reserva);

        for (var sesion : sesiones) {
            esError(vista(sesion, reserva), 403, ACCESO_DENEGADO);
            esError(cancelar(sesion, reserva, "161.00"), 403, ACCESO_DENEGADO);
            esError(confirmarRuta(sesion, rutaUsuario(reserva), "{}"), 403, ACCESO_DENEGADO);
            esError(confirmarRuta(sesion, rutaUsuario(reserva), null), 403, ACCESO_DENEGADO);
        }
        sinCambios(reserva, antes);
    }

    @Test
    @DisplayName("HU-15 R3, R36: USUARIO y ADMIN en la ruta del arrendador reciben 403, con o sin cuerpo, sin cambios")
    void rolAjenoEnLaRutaDelArrendador() throws Exception {
        var reserva = pagadaLejos();
        var sesiones = List.of(token(CORREO_USUARIO), tokenAdmin());
        var antes = foto(reserva);

        for (var sesion : sesiones) {
            esError(vistaDelArrendador(sesion, reserva), 403, ACCESO_DENEGADO);
            esError(cancelarDelArrendador(sesion, reserva, "161.00"), 403, ACCESO_DENEGADO);
            esError(confirmarRuta(sesion, rutaArrendador(reserva), "{}"), 403, ACCESO_DENEGADO);
            esError(confirmarRuta(sesion, rutaArrendador(reserva), null), 403, ACCESO_DENEGADO);
        }
        sinCambios(reserva, antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "12345678901234567890"})
    @DisplayName("HU-15 R4: un id mal escrito responde 404 en las cuatro rutas")
    void idMalEscrito(String id) throws Exception {
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);

        esError(vista(usuario, id), 404, NO_ENCONTRADO);
        esError(cancelar(usuario, id, "161.00"), 404, NO_ENCONTRADO);
        esError(vistaDelArrendador(arrendador, id), 404, NO_ENCONTRADO);
        esError(cancelarDelArrendador(arrendador, id, "161.00"), 404, NO_ENCONTRADO);
    }

    @Test
    @DisplayName("HU-15 R5, R36: la reserva de otro usuario o una inexistente responde 404 sin cambios")
    void ajenaDelUsuario() throws Exception {
        var reserva = pagadaLejos();
        crearUsuario(OTRO_CORREO);
        var otro = token(OTRO_CORREO);
        var antes = foto(reserva);

        esError(vista(otro, reserva), 404, NO_ENCONTRADO);
        esError(cancelar(otro, reserva, "161.00"), 404, NO_ENCONTRADO);
        esError(vista(otro, reserva + 1000), 404, NO_ENCONTRADO);
        esError(cancelar(otro, reserva + 1000, "161.00"), 404, NO_ENCONTRADO);
        sinCambios(reserva, antes);
        assertThat(bloquesActivos(espacioId)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-15 R6, R36: la reserva del espacio de otro arrendador o una inexistente responde 404 sin cambios")
    void ajenaDelArrendador() throws Exception {
        var ajeno = espacioDe(otroArrendador());
        var reserva = sembrar(usuarioId, ajeno, "CONFIRMADA", fecha, 18, 19);
        filaPagoPagado(reserva, "161.00");
        var arrendador = token(CORREO_ARRENDADOR);
        var antes = foto(reserva);

        esError(vistaDelArrendador(arrendador, reserva), 404, NO_ENCONTRADO);
        esError(cancelarDelArrendador(arrendador, reserva, "161.00"), 404, NO_ENCONTRADO);
        esError(vistaDelArrendador(arrendador, reserva + 1000), 404, NO_ENCONTRADO);
        esError(cancelarDelArrendador(arrendador, reserva + 1000, "161.00"), 404, NO_ENCONTRADO);
        sinCambios(reserva, antes);
    }

    @Test
    @DisplayName("HU-15 R7, R30: ignora porcentaje, monto, canceladaPor y usuarioId del cuerpo: < 48 h es 0 % del "
            + "USUARIO")
    void ignoraLoQueNoEsElMontoVisto() throws Exception {
        var reserva = pagadaCerca();
        var otro = crearUsuario(OTRO_CORREO);
        var cuerpo = "{\"montoReembolsoVisto\":0.00,\"porcentaje\":100,\"montoReembolso\":161,"
                + "\"politica\":\"ARRENDADOR\",\"canceladaPor\":\"ARRENDADOR\",\"usuarioId\":" + otro + "}";

        confirmarRuta(token(CORREO_USUARIO), rutaUsuario(reserva), cuerpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.canceladaPor").value("USUARIO"))
                .andExpect(jsonPath("$.politica").value("USUARIO_SIN_ANTICIPACION"))
                .andExpect(jsonPath("$.porcentaje").value(0))
                .andExpect(jsonPath("$.montoReembolso").value(0.0));

        var fila = reservaGuardada(reserva);
        assertThat(fila.get("cancelada_por")).isEqualTo("USUARIO");
        assertThat((BigDecimal) fila.get("monto_reembolso")).isEqualByComparingTo("0.00");
        assertThat(fila.get("usuario_id")).isEqualTo(usuarioId);
    }

    // ── Qué se puede cancelar (R8–R10) ───────────────────────────────────

    @Test
    @DisplayName("HU-15 R8, R36: una CANCELADA responde 400 ya cancelada en la vista previa y en la confirmación")
    void yaCancelada() throws Exception {
        var reserva = sembrar(usuarioId, espacioId, "CANCELADA", fecha, 18);
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);
        var antes = foto(reserva);

        esError(vista(usuario, reserva), 400, YA_CANCELADA);
        esError(cancelar(usuario, reserva, "80.50"), 400, YA_CANCELADA);
        esError(vistaDelArrendador(arrendador, reserva), 400, YA_CANCELADA);
        esError(cancelarDelArrendador(arrendador, reserva, "80.50"), 400, YA_CANCELADA);
        sinCambios(reserva, antes);
    }

    @Test
    @DisplayName("HU-15 R9, R36: PENDIENTE_PAGO vigente, vencida sin procesar y EXPIRADA responden 400 no confirmada")
    void noConfirmadas() throws Exception {
        var vigente = sembrar(usuarioId, espacioId, "PENDIENTE_PAGO", fecha, 10);
        var vencida = filaReserva(usuarioId, espacioId, "PENDIENTE_PAGO", haceMinutos(1));
        filaBloque(vencida, espacioId, fecha, 11, true);
        var expirada = sembrar(usuarioId, espacioId, "EXPIRADA", fecha, 12);
        var usuario = token(CORREO_USUARIO);

        for (var reserva : List.of(vigente, vencida, expirada)) {
            var antes = foto(reserva);
            esError(vista(usuario, reserva), 400, NO_CONFIRMADA);
            esError(cancelar(usuario, reserva, "80.50"), 400, NO_CONFIRMADA);
            sinCambios(reserva, antes);
        }
    }

    @Test
    @DisplayName("HU-15 R10, R36: una CONFIRMADA que ya empezó o terminó responde 400 ya iniciada, también al "
            + "arrendador")
    void yaIniciada() throws Exception {
        var reserva = pagada(hoy.minusDays(1), 10);
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);
        var antes = foto(reserva);

        esError(vista(usuario, reserva), 400, YA_INICIADA);
        esError(cancelar(usuario, reserva, "0.00"), 400, YA_INICIADA);
        esError(vistaDelArrendador(arrendador, reserva), 400, YA_INICIADA);
        esError(cancelarDelArrendador(arrendador, reserva, "161.00"), 400, YA_INICIADA);
        sinCambios(reserva, antes);
    }

    // ── Política (R11–R16) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R11, R12, R14: con 48 h exactas hasta el inicio en Lima es 100 %; un segundo después, 0 %")
    void limiteDeCuarentaYOchoHoras() throws Exception {
        // El reloj solo avanza: se lleva a la próxima hora en punto para que el inicio quede a 48 h exactas.
        var ahora = reloj.instant();
        reloj.avanzar(Duration.between(ahora, ahora.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1))));
        var inicio = LocalDateTime.ofInstant(reloj.instant().plus(Duration.ofHours(48)), ZonaHoraria.LIMA);
        var reserva = sembrar(usuarioId, espacioId, "CONFIRMADA", inicio.toLocalDate(), inicio.getHour());
        filaPagoPagado(reserva, "80.50");
        var usuario = token(CORREO_USUARIO);

        vista(usuario, reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.politica").value("USUARIO_CON_ANTICIPACION"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(80.5));

        reloj.avanzar(Duration.ofSeconds(1));

        vista(usuario, reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.politica").value("USUARIO_SIN_ANTICIPACION"))
                .andExpect(jsonPath("$.porcentaje").value(0))
                .andExpect(jsonPath("$.montoReembolso").value(0.0));
    }

    @Test
    @DisplayName("HU-15 R13, R38: el arrendador cancela con < 48 h al 100 % y la reserva sale de su lista")
    void arrendadorCancelaAlCien() throws Exception {
        var reserva = pagadaCerca();
        var arrendador = token(CORREO_ARRENDADOR);
        assertThat(ids(listado(mvc.perform(con(arrendador, get(DE_MIS_ESPACIOS + "?grupo=proximas"))))))
                .contains(reserva);

        vistaDelArrendador(arrendador, reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.politica").value("ARRENDADOR"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(161.0));
        cancelarDelArrendador(arrendador, reserva, "161.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.canceladaPor").value("ARRENDADOR"))
                .andExpect(jsonPath("$.politica").value("ARRENDADOR"))
                .andExpect(jsonPath("$.montoReembolso").value(161.0));

        assertThat(reservaGuardada(reserva).get("cancelada_por")).isEqualTo("ARRENDADOR");
        assertThat(ids(listado(mvc.perform(con(arrendador, get(DE_MIS_ESPACIOS + "?grupo=proximas"))))))
                .doesNotContain(reserva);
        assertThat(ids(listado(mvc.perform(con(arrendador, get(DE_MIS_ESPACIOS + "?grupo=pasadas"))))))
                .doesNotContain(reserva);
    }

    // ── Vista previa (R17, R18) ──────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R17: la vista previa del usuario responde exactamente las claves del VistaCancelacionDTO")
    void vistaPreviaDelUsuario() throws Exception {
        var reserva = pagadaLejos();

        var respuesta = vista(token(CORREO_USUARIO), reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(reserva))
                .andExpect(jsonPath("$.espacioNombre").value(NOMBRE))
                .andExpect(jsonPath("$.fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.horaInicio").value(18))
                .andExpect(jsonPath("$.horaFin").value(20))
                .andExpect(jsonPath("$.montoPagado").value(161.0))
                .andExpect(jsonPath("$.politica").value("USUARIO_CON_ANTICIPACION"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(161.0));

        assertThat(mapa(texto(respuesta)).keySet()).containsExactlyInAnyOrder("reservaId", "espacioNombre", "fecha",
                "horaInicio", "horaFin", "montoPagado", "politica", "porcentaje", "montoReembolso");
    }

    @Test
    @DisplayName("HU-15 R17, R18: las vistas previas del usuario y del arrendador no escriben en reserva, bloques, "
            + "pago ni auditoría")
    void vistaPreviaNoEscribe() throws Exception {
        var reserva = pagadaCerca();
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);
        var antes = foto(reserva);

        vista(usuario, reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.politica").value("USUARIO_SIN_ANTICIPACION"));
        vistaDelArrendador(arrendador, reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.espacioNombre").value(NOMBRE))
                .andExpect(jsonPath("$.politica").value("ARRENDADOR"));

        sinCambios(reserva, antes);
    }

    // ── Confirmación (R19–R29, R35) ──────────────────────────────────────

    @Test
    @DisplayName("HU-15 R19, R20: confirmar responde el CancelacionDTO y guarda quién, cuándo y el monto")
    void confirmaYGuarda() throws Exception {
        var reserva = pagadaLejos();
        var ahora = reloj.instant();

        var respuesta = cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(reserva))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.canceladaPor").value("USUARIO"))
                .andExpect(jsonPath("$.canceladaEn").value(ahora.toString()))
                .andExpect(jsonPath("$.politica").value("USUARIO_CON_ANTICIPACION"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(161.0));

        assertThat(mapa(texto(respuesta)).keySet()).containsExactlyInAnyOrder("reservaId", "estado", "canceladaPor",
                "canceladaEn", "politica", "porcentaje", "montoReembolso");
        var fila = reservaGuardada(reserva);
        assertThat(fila.get("estado")).isEqualTo("CANCELADA");
        assertThat(fila.get("cancelada_por")).isEqualTo("USUARIO");
        assertThat(instante(fila.get("cancelada_en"))).isEqualTo(ahora);
        assertThat((BigDecimal) fila.get("monto_reembolso")).isEqualByComparingTo("161.00");
    }

    @Test
    @DisplayName("HU-15 R21: confirmar libera todos los bloques y otro usuario puede reservar esas horas")
    void liberaLosBloques() throws Exception {
        var reserva = pagadaLejos();
        crearUsuario(OTRO_CORREO);
        var otro = token(OTRO_CORREO);
        esError(reservar(otro, espacioId, fecha, List.of(18, 19)), 400, HORARIO_NO_DISPONIBLE);

        cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk());

        assertThat(bloquesGuardados(reserva)).hasSize(2).allSatisfy(bloque -> assertThat(bloque.get("activo"))
                .isEqualTo(false));
        reservar(otro, espacioId, fecha, List.of(18, 19)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-15 R22, R24 · HU-16 R1, R11: con 100 % audita REEMBOLSO_SOLICITADO sobre el pago y el reembolso "
            + "automático deja el pago REEMBOLSADO")
    void solicitaElReembolso() throws Exception {
        var reserva = pagadaLejos();
        var pago = pagoDe(reserva);

        cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk());

        var registros = auditorias("REEMBOLSO_SOLICITADO");
        assertThat(registros).hasSize(1);
        var registro = registros.getFirst();
        assertThat(registro.get("entidad")).isEqualTo("pago");
        assertThat(registro.get("entidad_id")).isEqualTo(pago);
        assertThat(registro.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(registro.get("ip")).isEqualTo(ip);
        assertThat(registro.get("detalle")).isEqualTo("origen=CANCELACION;reservaId=" + reserva + ";monto=161.00");
        // HU-16: tras el commit, el reembolso automático contra la simulada deja el pago REEMBOLSADO.
        assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago))
                .isEqualTo("REEMBOLSADO");
        assertThat(filas("pago")).isEqualTo(1);
        var reembolsos = jdbc.queryForList("SELECT * FROM reembolso WHERE pago_id = ?", pago);
        assertThat(reembolsos).hasSize(1);
        assertThat(reembolsos.getFirst().get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(reembolsos.getFirst().get("origen")).isEqualTo("CANCELACION");
        assertThat(new BigDecimal(reembolsos.getFirst().get("monto").toString())).isEqualByComparingTo("161.00");
    }

    @Test
    @DisplayName("HU-15 R12, R23: con 0 % cancela igual pero no solicita ningún reembolso")
    void ceroPorCientoSinReembolso() throws Exception {
        var reserva = pagadaCerca();

        cancelar(token(CORREO_USUARIO), reserva, "0.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.porcentaje").value(0));

        assertThat(auditorias("RESERVA_CANCELADA")).hasSize(1);
        assertThat(auditorias("REEMBOLSO_SOLICITADO")).isEmpty();
        assertThat(bloquesActivos(espacioId)).isZero();
    }

    @Test
    @DisplayName("HU-15 R25, R29, R36: una CONFIRMADA sin pago PAGADO con 100 % responde 500 y queda como estaba")
    void sinPagoPagadoEsError() throws Exception {
        var reserva = sembrar(usuarioId, espacioId, "CONFIRMADA", fecha, 18, 19);
        var usuario = token(CORREO_USUARIO);
        var antes = foto(reserva);

        esError(cancelar(usuario, reserva, "161.00"), 500, INESPERADO);

        sinCambios(reserva, antes);
        assertThat(estadoGuardado(reserva)).isEqualTo("CONFIRMADA");
        assertThat(bloquesActivos(espacioId)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-15 R26, R27: tras confirmar se avisa al usuario con % y monto, y al arrendador sin montos")
    void enviaLosDosCorreos() throws Exception {
        var reserva = pagadaLejos();

        cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk());

        assertThat(notificador.alUsuario).containsExactly(new AvisoAlUsuario(CORREO_USUARIO,
                new AvisoCancelacionUsuario(NOMBRE, fecha, 18, 20, "USUARIO", 100, new BigDecimal("161.00"))));
        assertThat(notificador.alArrendador).containsExactly(new AvisoAlArrendador(CORREO_ARRENDADOR,
                new AvisoCancelacionArrendador(NOMBRE, fecha, 18, 20, "USUARIO")));
    }

    @Test
    @DisplayName("HU-25 R28: con los recordatorios desactivados, el usuario y el arrendador reciben igual los avisos "
            + "de cancelación")
    void cancelacionSinRecordatorios() throws Exception {
        jdbc.update("UPDATE usuario SET recordatorios_activos = false WHERE id IN (?, ?)", usuarioId, arrendadorId);
        var reserva = pagadaLejos();

        cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk());

        assertThat(notificador.alUsuario).extracting(AvisoAlUsuario::correo).containsExactly(CORREO_USUARIO);
        assertThat(notificador.alArrendador).extracting(AvisoAlArrendador::correo).containsExactly(CORREO_ARRENDADOR);
    }

    @Test
    @DisplayName("HU-15 R28: si falla el correo al usuario, la cancelación se mantiene, sale el del arrendador y "
            + "es 200")
    void unCorreoFallaYLaCancelacionSigue() throws Exception {
        var reserva = pagadaLejos();
        notificador.fallarAlUsuario();

        cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk());

        assertThat(estadoGuardado(reserva)).isEqualTo("CANCELADA");
        assertThat(notificador.alUsuario).isEmpty();
        assertThat(notificador.alArrendador).hasSize(1);
    }

    @Test
    @DisplayName("HU-15 R35: audita RESERVA_CANCELADA con la sesión, la IP, la fecha del reloj y el detalle")
    void auditaLaCancelacion() throws Exception {
        var reserva = pagadaLejos();
        var ahora = reloj.instant();

        cancelar(token(CORREO_USUARIO), reserva, "161.00").andExpect(status().isOk());

        var registros = auditorias("RESERVA_CANCELADA");
        assertThat(registros).hasSize(1);
        var registro = registros.getFirst();
        assertThat(registro.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(registro.get("entidad")).isEqualTo("reserva");
        assertThat(registro.get("entidad_id")).isEqualTo(reserva);
        assertThat(registro.get("ip")).isEqualTo(ip);
        assertThat(instante(registro.get("fecha"))).isEqualTo(ahora);
        assertThat(registro.get("detalle")).isEqualTo("canceladaPor=USUARIO;espacioId=" + espacioId
                + ";politica=USUARIO_CON_ANTICIPACION;porcentaje=100;monto=161.00");
    }

    // ── Monto visto (R31, R32) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-15 R31, R36: sin cuerpo, {}, -1 o 1.001 responden 400 en montoReembolsoVisto sin cambios")
    void montoVistoInvalido() throws Exception {
        var reserva = pagadaLejos();
        var usuario = token(CORREO_USUARIO);
        var antes = foto(reserva);

        esErrorDeMonto(confirmarRuta(usuario, rutaUsuario(reserva), null), MONTO_VISTO);
        esErrorDeMonto(confirmarRuta(usuario, rutaUsuario(reserva), "{}"), MONTO_VISTO);
        esErrorDeMonto(cancelar(usuario, reserva, "-1"), MONTO_VISTO);
        esErrorDeMonto(cancelar(usuario, reserva, "1.001"), MONTO_VISTO);
        sinCambios(reserva, antes);
    }

    @Test
    @DisplayName("HU-15 R32, R29, R36: 161.00 visto cuando el recalculado es 0.00 responde 400 monto cambió sin "
            + "cambios")
    void montoCambio() throws Exception {
        var reserva = pagadaCerca();
        var usuario = token(CORREO_USUARIO);
        var antes = foto(reserva);

        esErrorDeMonto(cancelar(usuario, reserva, "161.00"), MONTO_CAMBIO);

        sinCambios(reserva, antes);
        assertThat(estadoGuardado(reserva)).isEqualTo("CONFIRMADA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"161", "161.0"})
    @DisplayName("HU-15 R31, R32: 161 y 161.0 son el mismo monto que 161.00")
    void montoVistoSinDecimales(String monto) throws Exception {
        var reserva = pagadaLejos();

        cancelar(token(CORREO_USUARIO), reserva, monto).andExpect(status().isOk())
                .andExpect(jsonPath("$.montoReembolso").value(161.0));
    }

    // ── Después de cancelar (R37, R38) ───────────────────────────────────

    @Test
    @DisplayName("HU-15 R37, R38: la cancelada se lee CANCELADA en /api/reservas/{id} y en pasadas, y no la ve el "
            + "arrendador")
    void despuesDeCancelar() throws Exception {
        var reserva = pagadaLejos();
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);

        cancelar(usuario, reserva, "161.00").andExpect(status().isOk());

        consultar(usuario, reserva).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        var pasadas = listado(mvc.perform(con(usuario, get(RESERVAS + "?grupo=pasadas"))));
        assertThat(ids(pasadas)).contains(reserva);
        assertThat(pasadas.stream().filter(fila -> ((Number) fila.get("id")).longValue() == reserva).findFirst()
                .orElseThrow().get("estado")).isEqualTo("CANCELADA");
        assertThat(ids(listado(mvc.perform(con(usuario, get(RESERVAS + "?grupo=proximas")))))).doesNotContain(reserva);
        assertThat(ids(listado(mvc.perform(con(arrendador, get(DE_MIS_ESPACIOS + "?grupo=proximas"))))))
                .doesNotContain(reserva);
    }

    // ── Integridad en la base de datos (R39) ─────────────────────────────

    @Test
    @DisplayName("HU-15 R39: la base rechaza datos de cancelación incoherentes y acepta una CANCELADA sin ellos")
    void restriccionesDeCancelacion() {
        var confirmada = sembrar(usuarioId, espacioId, "CONFIRMADA", fecha, 9);
        var cancelada = sembrar(usuarioId, espacioId, "CANCELADA", fecha, 10);
        var completo = "cancelada_por = 'USUARIO', cancelada_en = now(), monto_reembolso = 80.50";

        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET " + completo + " WHERE id = ?", confirmada))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_cancelacion");
        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET cancelada_en = now() WHERE id = ?", cancelada))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_cancelacion");
        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET cancelada_por = 'OTRO', cancelada_en = now(), "
                + "monto_reembolso = 0 WHERE id = ?", cancelada))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_cancelada_por");
        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET cancelada_por = 'USUARIO', cancelada_en = now(), "
                + "monto_reembolso = -0.01 WHERE id = ?", cancelada))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_cancelacion");
        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET cancelada_por = 'USUARIO', cancelada_en = now(), "
                + "monto_reembolso = 80.51 WHERE id = ?", cancelada))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_cancelacion");

        assertThat(estadoGuardado(cancelada)).isEqualTo("CANCELADA");
        assertThat(jdbc.update("UPDATE reserva SET " + completo + " WHERE id = ?", cancelada)).isEqualTo(1);
    }

    // ── CA1–CA3 de punta a punta con la pasarela simulada ────────────────

    @Test
    @DisplayName("HU-15 R11, R17, R19, R21, R24: reservar, pagar, ver el 100 %, cancelar y volver a reservar las "
            + "mismas horas")
    void dePuntaAPunta() throws Exception {
        var usuario = token(CORREO_USUARIO);
        var dia = hoy.plusDays(5);
        franja(espacioId, dia, 8, 22);
        var reserva = idDe(reservar(usuario, espacioId, dia, List.of(18, 19)).andExpect(status().isCreated()));
        mvc.perform(con(usuario, post(RESERVAS + "/" + reserva + "/pago"))).andExpect(status().isOk());
        mvc.perform(con(usuario, post(RESERVAS + "/" + reserva + "/pago/simulacion"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isNoContent());
        assertThat(estadoGuardado(reserva)).isEqualTo("CONFIRMADA");

        var monto = mapa(texto(vista(usuario, reserva).andExpect(status().isOk())
                .andExpect(jsonPath("$.politica").value("USUARIO_CON_ANTICIPACION"))
                .andExpect(jsonPath("$.porcentaje").value(100)))).get("montoReembolso");
        cancelar(usuario, reserva, String.valueOf(monto)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        assertThat(auditorias("REEMBOLSO_SOLICITADO")).hasSize(1);
        assertThat(notificador.alUsuario).hasSize(1);
        crearUsuario(OTRO_CORREO);
        reservar(token(OTRO_CORREO), espacioId, dia, List.of(18, 19)).andExpect(status().isCreated());
    }
}
