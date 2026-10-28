package pe.edu.ulima.espacioya.comun.cifrado;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Huella determinística (HMAC-SHA256 en hexadecimal) para buscar o exigir unicidad sobre un
 * campo cifrado sin guardarlo en claro. Con llave secreta no se puede revertir probando valores.
 */
@Component
public class HuellaCampos {

    public static final String VARIABLE_LLAVE = "CIFRADO_LLAVE_HUELLA";

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec llave;

    public HuellaCampos(@Value("${espacioya.cifrado.llave-huella}") String llaveBase64) {
        this.llave = new SecretKeySpec(LlaveCifrado.decodificar(llaveBase64, VARIABLE_LLAVE), ALGORITMO);
    }

    public String calcular(String claro) {
        try {
            var mac = Mac.getInstance(ALGORITMO);
            mac.init(llave);
            return HexFormat.of().formatHex(mac.doFinal(claro.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la huella del campo", e);
        }
    }
}
