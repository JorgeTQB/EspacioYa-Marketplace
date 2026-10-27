package pe.edu.ulima.espacioya.cuentas.domain;

import java.nio.charset.StandardCharsets;

public final class PoliticaContrasena {

    public static final String REQUISITOS = "La contraseña debe tener entre 8 y 64 caracteres, "
            + "al menos una letra y un número, y no puede ser igual a tu correo.";

    private static final int LONGITUD_MINIMA = 8;
    private static final int LONGITUD_MAXIMA = 64;
    // bcrypt solo procesa los primeros 72 bytes: una contraseña más larga se truncaría en silencio.
    private static final int BYTES_MAXIMOS = 72;

    private PoliticaContrasena() {
    }

    public static boolean cumple(String contrasena, String correoNormalizado) {
        if (contrasena == null) {
            return false;
        }
        var longitud = contrasena.codePointCount(0, contrasena.length());
        return longitud >= LONGITUD_MINIMA
                && longitud <= LONGITUD_MAXIMA
                && contrasena.getBytes(StandardCharsets.UTF_8).length <= BYTES_MAXIMOS
                && contrasena.codePoints().anyMatch(Character::isLetter)
                && contrasena.chars().anyMatch(c -> c >= '0' && c <= '9')
                && !contrasena.equalsIgnoreCase(correoNormalizado);
    }
}
