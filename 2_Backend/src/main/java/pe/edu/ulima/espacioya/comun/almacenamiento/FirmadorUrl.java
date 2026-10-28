package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.cifrado.LlaveCifrado;

/**
 * Firma con HMAC-SHA256 las URL de corta duración de {@code GET /api/archivos} (HU-05, RNF-SEG-15).
 * La firma cubre la clave, el tipo MIME y el vencimiento: cambiar cualquiera la invalida.
 */
@Component
public class FirmadorUrl {

    public static final String VARIABLE_LLAVE = "ALMACENAMIENTO_LLAVE_FIRMA";
    public static final Duration DURACION = Duration.ofMinutes(5);
    public static final String RUTA = "/api/archivos";

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec llave;
    private final Clock reloj;

    public FirmadorUrl(@Value("${espacioya.almacenamiento.llave-firma}") String llave,
                       @Value("${espacioya.cifrado.llave}") String llaveCifrado,
                       @Value("${espacioya.cifrado.llave-huella}") String llaveHuella, Clock reloj) {
        var bytes = LlaveCifrado.decodificar(llave, VARIABLE_LLAVE);
        // Llaves separadas: filtrar la de firma no debe exponer el cifrado ni las huellas, ni al revés.
        if (Arrays.equals(bytes, LlaveCifrado.decodificar(llaveCifrado, CifradorCampos.VARIABLE_LLAVE))
                || Arrays.equals(bytes, LlaveCifrado.decodificar(llaveHuella, HuellaCampos.VARIABLE_LLAVE))) {
            throw new IllegalStateException(VARIABLE_LLAVE + " debe ser distinta de "
                    + CifradorCampos.VARIABLE_LLAVE + " y de " + HuellaCampos.VARIABLE_LLAVE);
        }
        this.llave = new SecretKeySpec(bytes, ALGORITMO);
        this.reloj = reloj;
    }

    public UrlFirmada firmar(String clave, String tipoMime) {
        var expira = Instant.now(reloj).plus(DURACION).getEpochSecond();
        var url = RUTA + "?clave=" + codificar(clave) + "&tipo=" + codificar(tipoMime)
                + "&expira=" + expira + "&firma=" + firma(clave, tipoMime, expira);
        return new UrlFirmada(url, Instant.ofEpochSecond(expira));
    }

    public boolean esValida(String clave, String tipoMime, long expira, String firma) {
        if (clave == null || tipoMime == null || firma == null) {
            return false;
        }
        if (!Instant.ofEpochSecond(expira).isAfter(Instant.now(reloj))) {
            return false;
        }
        var esperada = firma(clave, tipoMime, expira).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.US_ASCII));
    }

    private String firma(String clave, String tipoMime, long expira) {
        try {
            var mac = Mac.getInstance(ALGORITMO);
            mac.init(llave);
            var mensaje = clave + "\n" + tipoMime + "\n" + expira;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la URL", e);
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
