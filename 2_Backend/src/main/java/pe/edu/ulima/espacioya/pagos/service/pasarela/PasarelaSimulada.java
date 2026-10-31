package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.pagos.domain.ReglasPago;

/**
 * Pasarela simulada, para pruebas, desarrollo y UAT hasta conectar Mercado Pago (HU-13 D7 revisada, D8). El checkout es
 * la pantalla de pago simulado, y {@link #notificacionDe} arma lo que enviaría una pasarela: no decide nada, las reglas
 * siguen en {@code WebhookPagoService}. El token interno no sale del proceso y no es una firma de proveedor: no prueba
 * nada de la firma real (S1). Por {@code POST /api/pagos/webhook} solo se entra con el secreto configurado, si existe,
 * e id {@code sim-<reserva>-<APROBADO|RECHAZADO|SIN_RESULTADO>-<n>}: sin secreto, esa ruta rechaza todo pago (S16).
 */
@Component
@ConditionalOnProperty(name = "espacioya.pagos.pasarela", havingValue = "simulada")
public class PasarelaSimulada implements PasarelaPago {

    private static final Logger log = LoggerFactory.getLogger(PasarelaSimulada.class);
    private static final Pattern ID_SIMULADO =
            Pattern.compile("sim-([0-9]+)-(APROBADO|RECHAZADO|SIN_RESULTADO)-[0-9]+");
    private static final String ULTIMOS4_SIMULADOS = "4242";
    private static final String PANTALLA_SIMULADA = "/pago-simulado";
    private static final int BYTES_TOKEN = 32;

    private final Map<String, BigDecimal> montos = new ConcurrentHashMap<>();
    private final byte[] secretoWebhook;
    private final String tokenInterno;

    public PasarelaSimulada(@Value("${espacioya.pagos.secreto-webhook:}") String secretoWebhook) {
        this.secretoWebhook = secretoWebhook.getBytes(StandardCharsets.UTF_8);
        var aleatorios = new byte[BYTES_TOKEN];
        new SecureRandom().nextBytes(aleatorios);
        this.tokenInterno = HexFormat.of().formatHex(aleatorios);
        log.warn("Pasarela de pago SIMULADA activa: los pagos no pasan por ninguna pasarela certificada");
    }

    /** Lo que enviaría una pasarela. Recuerda el monto: tras un reinicio, el mapa está vacío (HU-13 DT-20, R72). */
    public NotificacionRecibida notificacionDe(Long reservaId, BigDecimal monto, ResultadoPasarela resultado,
                                               long intento) {
        montos.put(reservaId.toString(), monto);
        var id = "sim-" + reservaId + "-" + resultado.name() + "-" + intento;
        return new NotificacionRecibida(Map.of("type", "payment", "data.id", id), Map.of("x-signature", tokenInterno));
    }

    @Override
    public CheckoutCreado crearCheckout(SolicitudCheckout solicitud) {
        var reserva = solicitud.reservaId().toString();
        montos.put(reserva, solicitud.monto());
        return new CheckoutCreado("sim-checkout-" + reserva, solicitud.urlRetorno() + PANTALLA_SIMULADA);
    }

    @Override
    public void cerrarCheckout(String idCheckout) {
        log.info("Cierre de checkout simulado: no se llamó a ninguna pasarela");
    }

    @Override
    public Optional<String> idNotificado(NotificacionRecibida notificacion) {
        var parametros = notificacion.parametros();
        if (!"payment".equals(parametros.get("type"))) {
            return Optional.empty();
        }
        var id = parametros.get("data.id");
        var firma = notificacion.encabezados().get("x-signature");
        if (id == null || id.isBlank() || firma == null || !firmaValida(firma.getBytes(StandardCharsets.UTF_8))) {
            throw new FirmaInvalidaException();
        }
        return Optional.of(id);
    }

    // Sin cortocircuito: las dos comparaciones corren siempre, en tiempo constante.
    private boolean firmaValida(byte[] firma) {
        var conSecreto = secretoWebhook.length > 0 && MessageDigest.isEqual(firma, secretoWebhook);
        var conToken = MessageDigest.isEqual(firma, tokenInterno.getBytes(StandardCharsets.UTF_8));
        return conSecreto | conToken;
    }

    // Un id desconocido o un monto olvidado tras reiniciar es la misma falla que un 404 de la pasarela real (S10).
    @Override
    public PagoConsultado consultarPago(String idTransaccion) {
        var partes = ID_SIMULADO.matcher(idTransaccion);
        if (!partes.matches() || !montos.containsKey(partes.group(1))) {
            throw new ServicioExternoException();
        }
        var reserva = partes.group(1);
        return new PagoConsultado(idTransaccion, ResultadoPasarela.valueOf(partes.group(2)), montos.get(reserva),
                ReglasPago.MONEDA, ULTIMOS4_SIMULADOS, reserva);
    }
}
