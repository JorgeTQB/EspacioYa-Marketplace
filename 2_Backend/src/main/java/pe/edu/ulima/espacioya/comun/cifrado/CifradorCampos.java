package pe.edu.ulima.espacioya.comun.cifrado;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra campos sensibles en reposo con AES-256-GCM (RNF-SEG-14).
 * Formato guardado: {@code "v1:" + base64(iv(12) ‖ texto cifrado + tag(16))}.
 */
@Component
public class CifradorCampos {

    public static final String VARIABLE_LLAVE = "CIFRADO_LLAVE";

    private static final String PREFIJO = "v1:";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int BYTES_IV = 12;
    private static final int BITS_TAG = 128;

    private final SecretKeySpec llave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorCampos(@Value("${espacioya.cifrado.llave}") String llaveBase64,
                          @Value("${espacioya.cifrado.llave-huella}") String llaveHuellaBase64) {
        var bytes = LlaveCifrado.decodificar(llaveBase64, VARIABLE_LLAVE);
        var bytesHuella = LlaveCifrado.decodificar(llaveHuellaBase64, HuellaCampos.VARIABLE_LLAVE);
        // Llaves separadas: filtrar una no debe permitir descifrar ni recalcular huellas con la otra.
        if (Arrays.equals(bytes, bytesHuella)) {
            throw new IllegalStateException(VARIABLE_LLAVE + " y " + HuellaCampos.VARIABLE_LLAVE
                    + " deben ser distintas");
        }
        this.llave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(String claro) {
        if (claro == null) {
            return null;
        }
        var iv = new byte[BYTES_IV];
        aleatorio.nextBytes(iv);
        try {
            var cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, iv));
            var cifrado = cifrador.doFinal(claro.getBytes(StandardCharsets.UTF_8));
            var junto = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getEncoder().encodeToString(junto);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el campo", e);
        }
    }

    public String descifrar(String guardado) {
        if (guardado == null) {
            return null;
        }
        if (!guardado.startsWith(PREFIJO)) {
            throw new IllegalStateException("Formato de campo cifrado desconocido");
        }
        try {
            var junto = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            if (junto.length <= BYTES_IV) {
                throw new IllegalStateException("Campo cifrado incompleto");
            }
            var cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, junto, 0, BYTES_IV));
            var claro = cifrador.doFinal(junto, BYTES_IV, junto.length - BYTES_IV);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Sin el texto ni la llave en el mensaje: solo se informa que no se pudo descifrar.
            throw new IllegalStateException("No se pudo descifrar el campo: alterado o con otra llave");
        }
    }
}
