package pe.edu.ulima.espacioya.comun.cifrado;

import java.util.Base64;

final class LlaveCifrado {

    static final int BYTES_LLAVE = 32;

    private LlaveCifrado() {
    }

    // El mensaje nombra la variable de entorno y nunca muestra su valor.
    static byte[] decodificar(String llaveBase64, String variable) {
        if (llaveBase64 == null || llaveBase64.isBlank()) {
            throw llaveInvalida(variable);
        }
        byte[] llave;
        try {
            llave = Base64.getDecoder().decode(llaveBase64.trim());
        } catch (IllegalArgumentException e) {
            throw llaveInvalida(variable);
        }
        if (llave.length != BYTES_LLAVE) {
            throw llaveInvalida(variable);
        }
        return llave;
    }

    private static IllegalStateException llaveInvalida(String variable) {
        return new IllegalStateException("Falta " + variable
                + " o no es una llave de 32 bytes en base64 (genérala con: openssl rand -base64 32)");
    }
}
