package pe.edu.ulima.espacioya.comun.cifrado;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra archivos en reposo con AES-256-GCM y la llave {@code CIFRADO_LLAVE} (HU-05, RNF-SEG-14).
 * Formato guardado: {@code "EYA1" ‖ iv(12) ‖ contenido cifrado + tag(16)}. El prefijo permite cambiar
 * el formato más adelante sin migrar los archivos existentes.
 */
@Component
public class CifradorArchivos {

    private static final byte[] PREFIJO = "EYA1".getBytes(StandardCharsets.US_ASCII);
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int BYTES_IV = 12;
    private static final int BITS_TAG = 128;
    private static final int BYTES_TAG = BITS_TAG / 8;

    private final SecretKeySpec llave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorArchivos(@Value("${espacioya.cifrado.llave}") String llaveBase64) {
        this.llave = new SecretKeySpec(LlaveCifrado.decodificar(llaveBase64, CifradorCampos.VARIABLE_LLAVE), "AES");
    }

    public byte[] cifrar(byte[] claro) {
        var iv = new byte[BYTES_IV];
        aleatorio.nextBytes(iv);
        try {
            var cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, iv));
            var cifrado = cifrador.doFinal(claro);
            return ByteBuffer.allocate(PREFIJO.length + iv.length + cifrado.length)
                    .put(PREFIJO).put(iv).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el archivo", e);
        }
    }

    public byte[] descifrar(byte[] guardado) {
        var inicio = PREFIJO.length + BYTES_IV;
        if (guardado == null || guardado.length < inicio + BYTES_TAG
                || !Arrays.equals(guardado, 0, PREFIJO.length, PREFIJO, 0, PREFIJO.length)) {
            throw new IllegalStateException("Formato de archivo cifrado desconocido");
        }
        try {
            var cifrador = Cipher.getInstance(TRANSFORMACION);
            var iv = new GCMParameterSpec(BITS_TAG, guardado, PREFIJO.length, BYTES_IV);
            cifrador.init(Cipher.DECRYPT_MODE, llave, iv);
            return cifrador.doFinal(guardado, inicio, guardado.length - inicio);
        } catch (GeneralSecurityException e) {
            // Sin contenido ni llave en el mensaje: solo se informa que no se pudo descifrar.
            throw new IllegalStateException("No se pudo descifrar el archivo: alterado o con otra llave");
        }
    }
}
