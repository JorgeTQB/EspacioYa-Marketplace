package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.pagos.domain.ReglasPago;

/**
 * Adapter de Mercado Pago Checkout Pro (HU-13 D1). Solo traduce formatos, firma y estados; las reglas de negocio viven
 * en {@code ReglasPago}. Va con {@code RestClient} y no con el SDK oficial (DT-1).
 */
@Component
@ConditionalOnProperty(name = "espacioya.pagos.pasarela", havingValue = "mercadopago")
public class MercadoPagoAdapter implements PasarelaPago {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoAdapter.class);

    // Mercado Pago espera 22 s y el webhook hace a lo sumo 2 llamadas (DT-15).
    private static final Duration TIEMPO_CONEXION = Duration.ofSeconds(3);
    private static final Duration TIEMPO_LECTURA = Duration.ofSeconds(7);
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZonaHoraria.LIMA);
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() { };
    private static final Pattern CUATRO_DIGITOS = Pattern.compile("[0-9]{4}");
    private static final String HMAC = "HmacSHA256";
    private static final String TIPO_PAGO = "payment";
    private static final String ID_NOTIFICADO = "data.id";
    private static final String ID_NOTIFICADO_ALTERNO = "data_id";
    private static final String ENCABEZADO_IDEMPOTENCIA = "X-Idempotency-Key";
    private static final String REEMBOLSO_APROBADO = "approved";
    private static final List<String> TIPOS_EXCLUIDOS =
            List.of("ticket", "atm", "bank_transfer", "digital_wallet", "digital_currency");

    private final RestClient cliente;
    private final byte[] secretoWebhook;
    private final String urlNotificacion;
    private final Clock reloj;

    public MercadoPagoAdapter(@Value("${espacioya.pagos.llave-secreta:}") String llaveSecreta,
                              @Value("${espacioya.pagos.secreto-webhook:}") String secretoWebhook,
                              @Value("${espacioya.pagos.url-notificacion:}") String urlNotificacion,
                              @Value("${espacioya.pagos.url-api:https://api.mercadopago.com}") String urlApi,
                              Clock reloj) {
        this.secretoWebhook = obligatoria(secretoWebhook, "PAGOS_SECRETO_WEBHOOK").getBytes(StandardCharsets.UTF_8);
        this.urlNotificacion = obligatoria(urlNotificacion, "PAGOS_URL_NOTIFICACION");
        this.reloj = reloj;
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIEMPO_CONEXION).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(TIEMPO_LECTURA);
        this.cliente = RestClient.builder()
                .requestFactory(fabrica)
                .baseUrl(urlApi)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + obligatoria(llaveSecreta, "PAGOS_LLAVE_SECRETA"))
                .build();
    }

    @Override
    public CheckoutCreado crearCheckout(SolicitudCheckout solicitud) {
        var respuesta = llamar("crear el checkout", () -> cliente.post().uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .body(preferencia(solicitud))
                .retrieve()
                .body(MAPA));
        var id = texto(respuesta, "id");
        var url = texto(respuesta, "init_point");
        if (id == null || url == null) {
            log.warn("La pasarela creó un checkout sin id o sin URL");
            throw new ServicioExternoException();
        }
        return new CheckoutCreado(id, url);
    }

    @Override
    public void cerrarCheckout(String idCheckout) {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("expires", true);
        cuerpo.put("expiration_date_to", fecha(Instant.now(reloj)));
        llamar("cerrar el checkout", () -> cliente.put().uri("/checkout/preferences/{id}", idCheckout)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public Optional<String> idNotificado(NotificacionRecibida notificacion) {
        var parametros = notificacion.parametros();
        if (!TIPO_PAGO.equals(parametros.get("type"))) {
            return Optional.empty();
        }
        var id = parametros.containsKey(ID_NOTIFICADO) ? parametros.get(ID_NOTIFICADO)
                : parametros.get(ID_NOTIFICADO_ALTERNO);
        if (id == null || id.isBlank() || !firmaValida(id, notificacion.encabezados())) {
            throw new FirmaInvalidaException();
        }
        return Optional.of(id);
    }

    @Override
    public PagoConsultado consultarPago(String idTransaccion) {
        var pago = llamar("consultar el pago", () -> cliente.get().uri("/v1/payments/{id}", idTransaccion)
                .retrieve()
                .body(MAPA));
        var id = texto(pago, "id");
        var resultado = resultado(texto(pago, "status"));
        var monto = decimal(pago, "transaction_amount");
        if (id == null || (resultado != ResultadoPasarela.SIN_RESULTADO && monto == null)) {
            log.warn("La pasarela devolvió un pago sin id o sin monto");
            throw new ServicioExternoException();
        }
        return new PagoConsultado(id, resultado, monto, texto(pago, "currency_id"), ultimos4(pago),
                texto(pago, "external_reference"));
    }

    // La misma clave en cada intento: si una respuesta se perdió, Mercado Pago devuelve el reembolso ya hecho (HU-16 D6).
    @Override
    public ReembolsoRealizado reembolsar(SolicitudReembolsoPasarela solicitud) {
        var respuesta = llamar("reembolsar el pago", () -> cliente.post()
                .uri("/v1/payments/{id}/refunds", solicitud.idTransaccion())
                .contentType(MediaType.APPLICATION_JSON)
                .header(ENCABEZADO_IDEMPOTENCIA, solicitud.claveIdempotencia())
                .body(Map.of("amount", solicitud.monto()))
                .retrieve()
                .body(MAPA));
        var id = texto(respuesta, "id");
        if (id == null || !REEMBOLSO_APROBADO.equals(texto(respuesta, "status"))) {
            log.warn("La pasarela no aprobó el reembolso o respondió sin id");
            throw new ServicioExternoException();
        }
        return new ReembolsoRealizado(id);
    }

    private Map<String, Object> preferencia(SolicitudCheckout solicitud) {
        var item = new LinkedHashMap<String, Object>();
        item.put("title", "Reserva EspacioYa #" + solicitud.reservaId());
        item.put("quantity", 1);
        item.put("unit_price", solicitud.monto());
        item.put("currency_id", ReglasPago.MONEDA);

        var excluidos = TIPOS_EXCLUIDOS.stream().map(tipo -> Map.of("id", tipo)).toList();
        var mediosDePago = new LinkedHashMap<String, Object>();
        mediosDePago.put("installments", 1);
        mediosDePago.put("excluded_payment_types", excluidos);

        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("external_reference", solicitud.reservaId().toString());
        cuerpo.put("notification_url", urlNotificacion);
        cuerpo.put("binary_mode", true);
        cuerpo.put("expires", true);
        cuerpo.put("expiration_date_from", fecha(Instant.now(reloj)));
        cuerpo.put("expiration_date_to", fecha(solicitud.venceEn()));
        cuerpo.put("payment_methods", mediosDePago);
        // Mercado Pago no admite localhost en back_urls (S12): sin HTTPS, el comprador no vuelve solo.
        var retorno = solicitud.urlRetorno();
        if (retorno.startsWith("https://")) {
            var regreso = new LinkedHashMap<String, Object>();
            regreso.put("success", retorno + "?pago=exito");
            regreso.put("failure", retorno + "?pago=fallo");
            regreso.put("pending", retorno + "?pago=fallo");
            cuerpo.put("back_urls", regreso);
            cuerpo.put("auto_return", "approved");
        }
        return cuerpo;
    }

    // Sin tolerancia al ts (S8): repetir una notificación es inofensivo y descartarla podría perder una confirmación.
    private boolean firmaValida(String id, Map<String, String> encabezados) {
        var firma = partesDeFirma(encabezados.get("x-signature"));
        var idSolicitud = encabezados.get("x-request-id");
        if (firma.get("ts") == null || firma.get("v1") == null || idSolicitud == null) {
            return false;
        }
        var manifiesto = "id:" + id + ";request-id:" + idSolicitud + ";ts:" + firma.get("ts") + ";";
        return MessageDigest.isEqual(hmac(manifiesto), firma.get("v1").getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> partesDeFirma(String encabezado) {
        var partes = new LinkedHashMap<String, String>();
        if (encabezado == null) {
            return partes;
        }
        for (var parte : encabezado.split(",")) {
            var separador = parte.indexOf('=');
            if (separador > 0) {
                partes.put(parte.substring(0, separador).strip(), parte.substring(separador + 1).strip());
            }
        }
        return partes;
    }

    private byte[] hmac(String manifiesto) {
        try {
            var mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secretoWebhook, HMAC));
            var hex = HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
            return hex.getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no está disponible", e);
        }
    }

    // El detalle del proveedor queda fuera de la excepción: su cuerpo no debe llegar a ningún registro (R54).
    private static <T> T llamar(String operacion, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            log.warn("La pasarela respondió {} al {}", e.getStatusCode().value(), operacion);
            throw new ServicioExternoException();
        } catch (RestClientException e) {
            log.warn("Sin respuesta de la pasarela al {}", operacion);
            throw new ServicioExternoException();
        }
    }

    private static ResultadoPasarela resultado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "approved" -> ResultadoPasarela.APROBADO;
            case "rejected" -> ResultadoPasarela.RECHAZADO;
            default -> ResultadoPasarela.SIN_RESULTADO;
        };
    }

    private static String ultimos4(Map<String, Object> pago) {
        if (pago.get("card") instanceof Map<?, ?> tarjeta && tarjeta.get("last_four_digits") != null) {
            var digitos = tarjeta.get("last_four_digits").toString();
            return CUATRO_DIGITOS.matcher(digitos).matches() ? digitos : null;
        }
        return null;
    }

    private static String fecha(Instant instante) {
        return FORMATO_FECHA.format(instante);
    }

    private static String texto(Map<String, Object> mapa, String clave) {
        var valor = mapa == null ? null : mapa.get(clave);
        return valor == null ? null : valor.toString();
    }

    private static BigDecimal decimal(Map<String, Object> mapa, String clave) {
        var valor = texto(mapa, clave);
        return valor == null ? null : new BigDecimal(valor);
    }

    private static String obligatoria(String valor, String variable) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta " + variable + ": la pasarela mercadopago la necesita");
        }
        return valor.strip();
    }
}
