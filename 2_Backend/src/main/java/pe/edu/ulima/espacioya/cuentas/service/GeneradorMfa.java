package pe.edu.ulima.espacioya.cuentas.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.CodigoRespaldo;
import pe.edu.ulima.espacioya.cuentas.dto.ConfiguracionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;

@Component
public class GeneradorMfa {

    public static final int CANTIDAD_CODIGOS = 5;
    public static final int BYTES_SECRETO = 20;
    public static final int LARGO_CODIGO = 10;
    public static final String EMISOR = "EspacioYa";

    private static final Pattern SEPARADORES = Pattern.compile("[\\s-]");
    private static final int MITAD_CODIGO = LARGO_CODIGO / 2;

    private final HuellaCampos huella;
    private final CodigoRespaldoRepository codigos;
    private final SecureRandom aleatorio = new SecureRandom();

    public GeneradorMfa(HuellaCampos huella, CodigoRespaldoRepository codigos) {
        this.huella = huella;
        this.codigos = codigos;
    }

    public String nuevoSecreto() {
        var bytes = new byte[BYTES_SECRETO];
        aleatorio.nextBytes(bytes);
        return Base32.codificar(bytes);
    }

    public ConfiguracionMfaDTO configuracion(String correo, String secreto) {
        var cuenta = URLEncoder.encode(correo, StandardCharsets.UTF_8).replace("+", "%20");
        var uri = "otpauth://totp/" + EMISOR + ":" + cuenta + "?secret=" + secreto + "&issuer=" + EMISOR
                + "&algorithm=SHA1&digits=6&period=30";
        return new ConfiguracionMfaDTO(secreto, uri);
    }

    // Borra los anteriores: los códigos se muestran una sola vez y solo valen los de la última activación.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> reemplazarCodigos(Long usuarioId, Instant ahora) {
        codigos.borrarDeUsuario(usuarioId);
        var nuevos = new LinkedHashSet<String>();
        while (nuevos.size() < CANTIDAD_CODIGOS) {
            nuevos.add(codigoAleatorio());
        }
        var formateados = new ArrayList<String>(CANTIDAD_CODIGOS);
        for (var codigo : nuevos) {
            codigos.save(CodigoRespaldo.nuevo(usuarioId, huella(codigo), ahora));
            formateados.add(codigo.substring(0, MITAD_CODIGO) + "-" + codigo.substring(MITAD_CODIGO));
        }
        return List.copyOf(formateados);
    }

    public static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        return SEPARADORES.matcher(codigo).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public String huella(String codigoNormalizado) {
        return huella.calcular(codigoNormalizado);
    }

    private String codigoAleatorio() {
        var codigo = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            codigo.append(Base32.ALFABETO.charAt(aleatorio.nextInt(Base32.ALFABETO.length())));
        }
        return codigo.toString();
    }
}
