package pe.edu.ulima.espacioya.cuentas.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos TOTP de RFC 6238 con HMAC-SHA1, 6 dígitos, {@code T0 = 0} y paso de 30 s (HU-17 DT-4). SHA-1 porque es el
 * único algoritmo que respetan todas las apps de autenticación comunes.
 */
public final class Totp {

    public static final int DIGITOS = 6;
    public static final long SEGUNDOS_PASO = 30;
    public static final int TOLERANCIA_PASOS = 1;

    private static final String ALGORITMO = "HmacSHA1";
    private static final int MODULO = 1_000_000;
    private static final Pattern SEIS_DIGITOS = Pattern.compile("^[0-9]{6}$");

    private Totp() {
    }

    public static long pasoDe(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), SEGUNDOS_PASO);
    }

    public static String codigo(byte[] secreto, long paso) {
        try {
            var mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            var hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            // Truncamiento dinámico de RFC 4226 § 5.3.
            int desplazamiento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[desplazamiento] & 0x7f) << 24)
                    | ((hash[desplazamiento + 1] & 0xff) << 16)
                    | ((hash[desplazamiento + 2] & 0xff) << 8)
                    | (hash[desplazamiento + 3] & 0xff);
            return String.format("%06d", binario % MODULO);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible en la JVM", e);
        }
    }

    // Prueba el paso actual, el anterior y el siguiente, en ese orden. Un paso igual o anterior al último aceptado no
    // vale: un código ya visto no sirve dos veces (RFC 6238 § 5.2).
    public static OptionalLong pasoAceptado(byte[] secreto, String codigo, Instant ahora, Long ultimoPaso) {
        if (codigo == null) {
            return OptionalLong.empty();
        }
        var limpio = codigo.replace(" ", "");
        if (!SEIS_DIGITOS.matcher(limpio).matches()) {
            return OptionalLong.empty();
        }
        var enviado = limpio.getBytes(StandardCharsets.US_ASCII);
        var actual = pasoDe(ahora);
        long[] candidatos = {actual, actual - TOLERANCIA_PASOS, actual + TOLERANCIA_PASOS};
        for (long paso : candidatos) {
            if (ultimoPaso != null && paso <= ultimoPaso) {
                continue;
            }
            var esperado = codigo(secreto, paso).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(esperado, enviado)) {
                return OptionalLong.of(paso);
            }
        }
        return OptionalLong.empty();
    }
}
