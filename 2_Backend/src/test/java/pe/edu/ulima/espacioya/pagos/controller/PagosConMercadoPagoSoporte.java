package pe.edu.ulima.espacioya.pagos.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.pagos.service.pasarela.StubMercadoPago;

/**
 * IT con el {@code MercadoPagoAdapter} real contra {@link StubMercadoPago}: firma, consultas y cierres pasan por HTTP
 * de verdad. Las llaves son solo de prueba.
 */
@SpringBootTest(properties = {
        "espacioya.pagos.pasarela=mercadopago",
        "espacioya.pagos.llave-secreta=llave-solo-pruebas",
        "espacioya.pagos.url-notificacion=https://ejemplo.invalid/api/pagos/webhook"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
abstract class PagosConMercadoPagoSoporte extends PagosSoporte {

    static final String SECRETO = "secreto-solo-pruebas";
    static final String WEBHOOK = "/api/pagos/webhook";
    static final String TS = "1704908010";

    static final StubMercadoPago DOBLE = levantarDoble();

    private static final AtomicInteger SIGUIENTE_PAGO = new AtomicInteger(185919482);
    private static final AtomicInteger SIGUIENTE_SOLICITUD = new AtomicInteger(1);

    private static StubMercadoPago levantarDoble() {
        try {
            return new StubMercadoPago();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void urlDeLaApi(DynamicPropertyRegistry registro) {
        registro.add("espacioya.pagos.url-api", DOBLE::url);
    }

    @BeforeEach
    void reiniciarDoble() {
        DOBLE.reiniciar();
    }

    String idDePago() {
        return String.valueOf(SIGUIENTE_PAGO.getAndIncrement());
    }

    /** Un pago de {@link #MONTO} en soles, registrado en el doble para la reserva dada. */
    String pagoEnLaPasarela(String estado, long reserva) {
        var id = idDePago();
        DOBLE.pago(id, estado, MONTO, "PEN", String.valueOf(reserva), "1234");
        return id;
    }

    /** Lo que envía Mercado Pago: {@code type=payment}, {@code data.id} y su firma, sin cuerpo que importe. */
    ResultActions notificar(String idPago) throws Exception {
        var idSolicitud = "req-" + SIGUIENTE_SOLICITUD.getAndIncrement();
        return mvc.perform(desde(post(WEBHOOK).queryParam("type", "payment").queryParam("data.id", idPago)
                        .header("x-signature", firma(idPago, idSolicitud, TS, SECRETO))
                        .header("x-request-id", idSolicitud)
                        .contentType("application/json")
                        .content("{\"action\":\"payment.updated\"}"), ip));
    }

    static String firma(String id, String idSolicitud, String ts, String secreto) {
        var manifiesto = "id:" + id + ";request-id:" + idSolicitud + ";ts:" + ts + ";";
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hex = HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
            return "ts=" + ts + ",v1=" + hex;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
