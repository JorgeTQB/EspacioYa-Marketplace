package pe.edu.ulima.espacioya.comun.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class TokenAleatorio {

    public static final int BYTES = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private TokenAleatorio() {
    }

    public static String generar() {
        var bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean tieneFormato(String token) {
        return token != null && FORMATO.matcher(token).matches();
    }

    public static String hash(String token) {
        try {
            var resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
