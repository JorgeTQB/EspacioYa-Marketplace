package pe.edu.ulima.espacioya.pagos.service.pasarela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class MercadoPagoAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-10-28T15:14:00Z");
    private static final String SECRETO = "secreto-solo-pruebas";
    private static final String LLAVE = "llave-solo-pruebas";
    private static final String URL_NOTIFICACION = "https://ejemplo.invalid/api/pagos/webhook";
    private static final String ID_VECTOR = "185919482939";
    private static final String TS_VECTOR = "1704908010";
    private static final String V1_VECTOR = "fb7ed4b3bca94a9f1d45fbbde824844a98e2e4be60b95470649d3d62a0085ebf";
    private static final String FIRMA_VECTOR = "ts=" + TS_VECTOR + ",v1=" + V1_VECTOR;
    private static final SolicitudCheckout SOLICITUD = new SolicitudCheckout(7L, new BigDecimal("241.50"), VENCE,
            "http://localhost:5173/reservas/7");
    private static final SolicitudReembolsoPasarela REEMBOLSO = new SolicitudReembolsoPasarela("123",
            new BigDecimal("161.00"), "espacioya-reembolso-4");
    private static final String SECRETO_DEL_PROVEEDOR = "detalle-interno-del-proveedor";
    private static final String CUERPO_DEL_PROVEEDOR = "{\"message\":\"" + SECRETO_DEL_PROVEEDOR + "\"}";

    private static StubMercadoPago doble;

    private final JsonMapper json = JsonMapper.builder().build();
    private MercadoPagoAdapter adaptador;

    @BeforeAll
    static void levantarDoble() throws IOException {
        doble = new StubMercadoPago();
    }

    @AfterAll
    static void apagarDoble() {
        doble.close();
    }

    @BeforeEach
    void preparar() {
        doble.reiniciar();
        adaptador = new MercadoPagoAdapter(LLAVE, SECRETO, URL_NOTIFICACION, doble.url(),
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private Map<String, Object> cuerpoEnviado() {
        return json.readValue(doble.solicitudes("POST").getLast().cuerpo(), new TypeReference<>() { });
    }

    private static NotificacionRecibida notificacion(Map<String, String> parametros, String firma, String idSolicitud) {
        var encabezados = new HashMap<String, String>();
        if (firma != null) {
            encabezados.put("X-Signature", firma);
        }
        if (idSolicitud != null) {
            encabezados.put("X-Request-Id", idSolicitud);
        }
        return new NotificacionRecibida(parametros, encabezados);
    }

    private static NotificacionRecibida notificacionDelVector(String firma) {
        return notificacion(Map.of("type", "payment", "data.id", ID_VECTOR), firma, "r-1");
    }

    // ── Crear el checkout ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R1: devuelve el id y el init_point, nunca el sandbox_init_point")
    void devuelveElInitPoint() {
        var creado = adaptador.crearCheckout(SOLICITUD);

        assertThat(creado.idCheckout()).isEqualTo("pref-1");
        assertThat(creado.urlPago()).isEqualTo(StubMercadoPago.URL_PAGO + "pref-1");
    }

    @Test
    @DisplayName("HU-13 R2: envía el monto como unit_price, PEN y el id de la reserva como external_reference")
    void enviaMontoMonedaYReferencia() {
        adaptador.crearCheckout(SOLICITUD);

        var solicitud = doble.solicitudes("POST").getLast();
        assertThat(solicitud.ruta()).isEqualTo("/checkout/preferences");
        assertThat(solicitud.autorizacion()).isEqualTo("Bearer " + LLAVE);
        assertThat(solicitud.cuerpo()).contains("\"unit_price\":241.50");
        var cuerpo = cuerpoEnviado();
        assertThat(cuerpo.get("external_reference")).isEqualTo("7");
        assertThat(cuerpo.get("items")).isEqualTo(List.of(Map.of("title", "Reserva EspacioYa #7", "quantity", 1,
                "unit_price", 241.5, "currency_id", "PEN")));
    }

    @Test
    @DisplayName("HU-13 R4: envía expires y expiration_date_to en ISO 8601 con desfase de Lima")
    void enviaElVencimiento() {
        adaptador.crearCheckout(SOLICITUD);

        var cuerpo = cuerpoEnviado();
        assertThat(cuerpo.get("expires")).isEqualTo(true);
        assertThat(cuerpo.get("expiration_date_from")).isEqualTo("2026-10-28T10:00:00.000-05:00");
        assertThat(cuerpo.get("expiration_date_to")).isEqualTo("2026-10-28T10:14:00.000-05:00");
    }

    @Test
    @DisplayName("HU-13 R5: envía notification_url, binary_mode, una cuota y excluye lo que no es tarjeta, sin pagador")
    void enviaLaConfiguracionDelCheckout() {
        adaptador.crearCheckout(SOLICITUD);

        var cuerpo = cuerpoEnviado();
        assertThat(cuerpo.get("notification_url")).isEqualTo(URL_NOTIFICACION);
        assertThat(cuerpo.get("binary_mode")).isEqualTo(true);
        assertThat(cuerpo.get("payment_methods")).isEqualTo(Map.of("installments", 1, "excluded_payment_types",
                List.of(Map.of("id", "ticket"), Map.of("id", "atm"), Map.of("id", "bank_transfer"),
                        Map.of("id", "digital_wallet"), Map.of("id", "digital_currency"))));
        assertThat(cuerpo).doesNotContainKey("payer");
    }

    @Test
    @DisplayName("HU-13 R5: con frontend sin HTTPS no envía back_urls ni auto_return")
    void sinHttpsNoHayRetorno() {
        adaptador.crearCheckout(SOLICITUD);

        assertThat(cuerpoEnviado()).doesNotContainKeys("back_urls", "auto_return");
    }

    @Test
    @DisplayName("HU-13 R5: con frontend HTTPS envía back_urls con ?pago= y auto_return")
    void conHttpsHayRetorno() {
        adaptador.crearCheckout(new SolicitudCheckout(7L, new BigDecimal("241.50"), VENCE,
                "https://espacioya.invalid/reservas/7"));

        var cuerpo = cuerpoEnviado();
        assertThat(cuerpo.get("back_urls")).isEqualTo(Map.of(
                "success", "https://espacioya.invalid/reservas/7?pago=exito",
                "failure", "https://espacioya.invalid/reservas/7?pago=fallo",
                "pending", "https://espacioya.invalid/reservas/7?pago=fallo"));
        assertThat(cuerpo.get("auto_return")).isEqualTo("approved");
    }

    @Test
    @DisplayName("HU-13 R17: si la pasarela responde 500 al crear, lanza ServicioExternoException")
    void crearConError() {
        doble.estadoPreferencia(500);

        assertThatThrownBy(() -> adaptador.crearCheckout(SOLICITUD)).isInstanceOf(ServicioExternoException.class);
    }

    @Test
    @DisplayName("HU-13 R17: una respuesta sin init_point es un checkout no creado")
    void crearSinInitPoint() {
        doble.cuerpoPreferencia("{\"id\":\"pref-9\"}");

        assertThatThrownBy(() -> adaptador.crearCheckout(SOLICITUD)).isInstanceOf(ServicioExternoException.class);
    }

    @Test
    @DisplayName("HU-13 R17: si la pasarela no responde a tiempo, lanza ServicioExternoException")
    void crearConTiempoAgotado() {
        doble.demora(Duration.ofSeconds(8));

        assertThatThrownBy(() -> adaptador.crearCheckout(SOLICITUD)).isInstanceOf(ServicioExternoException.class);
    }

    @Test
    @DisplayName("HU-13 R40: cerrar el checkout hace PUT con expires y expiration_date_to = ahora")
    void cierraElCheckout() {
        adaptador.cerrarCheckout("pref-1");

        var solicitud = doble.solicitudes("PUT").getLast();
        assertThat(solicitud.ruta()).isEqualTo("/checkout/preferences/pref-1");
        assertThat(json.readValue(solicitud.cuerpo(), new TypeReference<Map<String, Object>>() { }))
                .isEqualTo(Map.of("expires", true, "expiration_date_to", "2026-10-28T10:00:00.000-05:00"));
    }

    @Test
    @DisplayName("HU-13 R41: si el cierre falla, lanza ServicioExternoException")
    void cerrarConError() {
        doble.estadoCierre(500);

        assertThatThrownBy(() -> adaptador.cerrarCheckout("pref-1")).isInstanceOf(ServicioExternoException.class);
    }

    // ── Firma ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R23: topic=payment y merchant_order no son una notificación de pago y no exigen firma")
    void otrosFormatosSeIgnoran() {
        assertThat(adaptador.idNotificado(notificacion(Map.of("topic", "payment", "id", "1"), null, null))).isEmpty();
        assertThat(adaptador.idNotificado(notificacion(Map.of("topic", "merchant_order", "id", "1"), null, null)))
                .isEmpty();
        assertThat(adaptador.idNotificado(notificacion(Map.of("type", "subscription", "data.id", "1"), null, null)))
                .isEmpty();
        assertThat(doble.solicitudes()).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R24: acepta el vector de firma aceptado por el validador oficial")
    void aceptaElVector() {
        assertThat(adaptador.idNotificado(notificacionDelVector(FIRMA_VECTOR))).contains(ID_VECTOR);
    }

    @Test
    @DisplayName("HU-13 R24: acepta el id en data_id, con la misma firma")
    void aceptaDataId() {
        var conDataId = notificacion(Map.of("type", "payment", "data_id", ID_VECTOR), FIRMA_VECTOR, "r-1");

        assertThat(adaptador.idNotificado(conDataId)).contains(ID_VECTOR);
    }

    @Test
    @DisplayName("HU-13 R24: con data.id y data_id manda data.id")
    void dataIdPunteadoTienePrioridad() {
        var conLosDos = notificacion(Map.of("type", "payment", "data.id", ID_VECTOR, "data_id", "999"), FIRMA_VECTOR,
                "r-1");

        assertThat(adaptador.idNotificado(conLosDos)).contains(ID_VECTOR);
    }

    @Test
    @DisplayName("HU-13 R24: rechaza un id alterado, un x-request-id alterado y un secreto distinto")
    void rechazaLoAlterado() {
        var otroId = notificacion(Map.of("type", "payment", "data.id", "185919482940"), FIRMA_VECTOR, "r-1");
        var otraSolicitud = notificacion(Map.of("type", "payment", "data.id", ID_VECTOR), FIRMA_VECTOR, "r-2");
        var otroSecreto = new MercadoPagoAdapter(LLAVE, "otro-secreto", URL_NOTIFICACION, doble.url(),
                Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> adaptador.idNotificado(otroId)).isInstanceOf(FirmaInvalidaException.class);
        assertThatThrownBy(() -> adaptador.idNotificado(otraSolicitud)).isInstanceOf(FirmaInvalidaException.class);
        assertThatThrownBy(() -> otroSecreto.idNotificado(notificacionDelVector(FIRMA_VECTOR)))
                .isInstanceOf(FirmaInvalidaException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "basura", "ts=1704908011,v1=" + V1_VECTOR, "ts=" + TS_VECTOR, "v1=" + V1_VECTOR,
            "ts=" + TS_VECTOR + ",v1=", "ts=" + TS_VECTOR + ",v1=abc", ",,,"})
    @DisplayName("HU-13 R24: rechaza un x-signature ausente, malformado o con otro ts")
    void rechazaFirmaMalformada(String firma) {
        assertThatThrownBy(() -> adaptador.idNotificado(notificacionDelVector(firma)))
                .isInstanceOf(FirmaInvalidaException.class);
    }

    @Test
    @DisplayName("HU-13 R24: rechaza una notificación de pago sin id o sin x-request-id")
    void rechazaSinIdOSinSolicitud() {
        var sinId = notificacion(Map.of("type", "payment"), FIRMA_VECTOR, "r-1");
        var sinSolicitud = notificacion(Map.of("type", "payment", "data.id", ID_VECTOR), FIRMA_VECTOR, null);

        assertThatThrownBy(() -> adaptador.idNotificado(sinId)).isInstanceOf(FirmaInvalidaException.class);
        assertThatThrownBy(() -> adaptador.idNotificado(sinSolicitud)).isInstanceOf(FirmaInvalidaException.class);
    }

    // ── Consultar el pago ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R25, R26: consulta GET /v1/payments/{id} y toma de ahí estado, monto, tarjeta y referencia")
    void traduceUnPagoAprobado() {
        doble.pago("555", "approved", "241.5", "PEN", "7", "1234");

        var pago = adaptador.consultarPago("555");

        assertThat(pago).isEqualTo(new PagoConsultado("555", ResultadoPasarela.APROBADO, new BigDecimal("241.5"), "PEN",
                "1234", "7"));
        var solicitud = doble.solicitudes("GET").getLast();
        assertThat(solicitud.ruta()).isEqualTo("/v1/payments/555");
        assertThat(solicitud.autorizacion()).isEqualTo("Bearer " + LLAVE);
    }

    @Test
    @DisplayName("HU-13 R39: status rejected es RECHAZADO")
    void traduceUnPagoRechazado() {
        doble.pago("555", "rejected", "241.50", "PEN", "7", "1234");

        assertThat(adaptador.consultarPago("555").resultado()).isEqualTo(ResultadoPasarela.RECHAZADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_process", "authorized", "refunded", "cancelled", "charged_back"})
    @DisplayName("HU-13 R28: cualquier otro status es SIN_RESULTADO")
    void otrosEstadosNoTienenResultado(String estado) {
        doble.pago("555", estado, "241.50", "PEN", "7", "1234");

        assertThat(adaptador.consultarPago("555").resultado()).isEqualTo(ResultadoPasarela.SIN_RESULTADO);
    }

    @Test
    @DisplayName("HU-13 R30: sin tarjeta o con últimos 4 que no son 4 dígitos, ultimos4 es nulo")
    void ultimos4Ausentes() {
        doble.pago("555", "approved", "241.50", "PEN", "7", null);
        doble.pago("556", "approved", "241.50", "PEN", "7", "12a4");
        doble.pago("557", """
                {"id":557,"status":"approved","transaction_amount":241.50,"currency_id":"PEN",
                 "external_reference":"7"}""");

        assertThat(adaptador.consultarPago("555").ultimos4()).isNull();
        assertThat(adaptador.consultarPago("556").ultimos4()).isNull();
        assertThat(adaptador.consultarPago("557").ultimos4()).isNull();
    }

    @Test
    @DisplayName("HU-13 R29: un external_reference nulo llega nulo, sin fallar")
    void referenciaNula() {
        doble.pago("555", "approved", "241.50", "PEN", null, "1234");

        assertThat(adaptador.consultarPago("555").referencia()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 404, 401})
    @DisplayName("HU-13 R27: si la consulta falla, incluido un 404, lanza ServicioExternoException")
    void consultaConError(int estado) {
        doble.estadoDeConsulta("555", estado);

        assertThatThrownBy(() -> adaptador.consultarPago("555")).isInstanceOf(ServicioExternoException.class);
    }

    @Test
    @DisplayName("HU-13 R27: un pago aprobado sin monto es una consulta fallida")
    void aprobadoSinMonto() {
        doble.pago("555", "{\"id\":555,\"status\":\"approved\"}");

        assertThatThrownBy(() -> adaptador.consultarPago("555")).isInstanceOf(ServicioExternoException.class);
    }

    // ── Reembolsar (HU-16) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-16 R12: reembolsa con POST /v1/payments/{id}/refunds, amount, la llave y X-Idempotency-Key")
    void enviaElReembolso() {
        var realizado = adaptador.reembolsar(REEMBOLSO);

        assertThat(realizado).isEqualTo(new ReembolsoRealizado("9001"));
        var solicitud = doble.solicitudes("POST").getLast();
        assertThat(solicitud.ruta()).isEqualTo("/v1/payments/123/refunds");
        assertThat(solicitud.autorizacion()).isEqualTo("Bearer " + LLAVE);
        assertThat(solicitud.claveIdempotencia()).isEqualTo("espacioya-reembolso-4");
        assertThat(solicitud.cuerpo()).isEqualTo("{\"amount\":161.00}");
    }

    @Test
    @DisplayName("HU-16 R12: con status approved e id devuelve ese id, aunque venga como texto")
    void reembolsoAprobadoConIdDeTexto() {
        doble.cuerpoReembolso("{\"id\":\"rf-77\",\"status\":\"approved\"}");

        assertThat(adaptador.reembolsar(REEMBOLSO).idReembolso()).isEqualTo("rf-77");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500})
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("HU-16 R13: si el reembolso responde con error, lanza ServicioExternoException sin registrar el cuerpo")
    void reembolsoConError(int estado, CapturedOutput salida) {
        doble.estadoReembolso(estado);
        doble.cuerpoReembolso(CUERPO_DEL_PROVEEDOR);

        assertThatThrownBy(() -> adaptador.reembolsar(REEMBOLSO)).isInstanceOf(ServicioExternoException.class);
        assertThat(salida.getAll()).doesNotContain(SECRETO_DEL_PROVEEDOR);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("HU-16 R13: si el reembolso no responde a tiempo, lanza ServicioExternoException")
    void reembolsoConTiempoAgotado(CapturedOutput salida) {
        doble.demora(Duration.ofSeconds(8));
        doble.cuerpoReembolso(CUERPO_DEL_PROVEEDOR);

        assertThatThrownBy(() -> adaptador.reembolsar(REEMBOLSO)).isInstanceOf(ServicioExternoException.class);
        assertThat(salida.getAll()).doesNotContain(SECRETO_DEL_PROVEEDOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"approved\",\"detalle\":\"" + SECRETO_DEL_PROVEEDOR + "\"}",
            "{\"id\":9001,\"status\":\"rejected\",\"detalle\":\"" + SECRETO_DEL_PROVEEDOR + "\"}",
            "{\"id\":9001,\"status\":\"in_process\",\"detalle\":\"" + SECRETO_DEL_PROVEEDOR + "\"}",
            "{\"id\":9001,\"detalle\":\"" + SECRETO_DEL_PROVEEDOR + "\"}"})
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("HU-16 R13: sin id o con status distinto de approved, lanza ServicioExternoException sin el cuerpo")
    void reembolsoNoAprobado(String cuerpo, CapturedOutput salida) {
        doble.cuerpoReembolso(cuerpo);

        assertThatThrownBy(() -> adaptador.reembolsar(REEMBOLSO)).isInstanceOf(ServicioExternoException.class);
        assertThat(salida.getAll()).doesNotContain(SECRETO_DEL_PROVEEDOR);
    }
}
