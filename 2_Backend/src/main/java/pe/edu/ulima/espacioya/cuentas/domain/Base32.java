package pe.edu.ulima.espacioya.cuentas.domain;

import java.io.ByteArrayOutputStream;

/** Base32 de RFC 4648 (alfabeto {@code A–Z2–7}) sin relleno, para los secretos TOTP y los códigos de respaldo. */
public final class Base32 {

    public static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final int BITS_POR_CARACTER = 5;
    private static final int MASCARA = 0x1f;

    private Base32() {
    }

    public static String codificar(byte[] datos) {
        var texto = new StringBuilder((datos.length * 8 + BITS_POR_CARACTER - 1) / BITS_POR_CARACTER);
        int acumulado = 0;
        int bits = 0;
        for (byte dato : datos) {
            acumulado = (acumulado << 8) | (dato & 0xff);
            bits += 8;
            while (bits >= BITS_POR_CARACTER) {
                bits -= BITS_POR_CARACTER;
                texto.append(ALFABETO.charAt((acumulado >> bits) & MASCARA));
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO.charAt((acumulado << (BITS_POR_CARACTER - bits)) & MASCARA));
        }
        return texto.toString();
    }

    public static byte[] decodificar(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("El texto Base32 es nulo");
        }
        var salida = new ByteArrayOutputStream(texto.length() * BITS_POR_CARACTER / 8);
        int acumulado = 0;
        int bits = 0;
        for (int i = 0; i < texto.length(); i++) {
            int valor = ALFABETO.indexOf(texto.charAt(i));
            if (valor < 0) {
                throw new IllegalArgumentException("El texto no es Base32 válido");
            }
            acumulado = (acumulado << BITS_POR_CARACTER) | valor;
            bits += BITS_POR_CARACTER;
            if (bits >= 8) {
                bits -= 8;
                salida.write((acumulado >> bits) & 0xff);
            }
        }
        return salida.toByteArray();
    }

    public static boolean esBase32(String texto) {
        if (texto == null || texto.isEmpty()) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            if (ALFABETO.indexOf(texto.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
