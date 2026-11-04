package pe.edu.ulima.espacioya.comun.seguridad;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Lista de IPs desde las que se entra al panel admin (HU-06, D2; RNF-SEG-04). Complementa a MFA (HU-17, P3):
 * {@code 0.0.0.0/0,::/0} la abre a cualquier IP. Admite direcciones IPv4 o IPv6 y rangos CIDR. Una lista vacía no
 * permite ninguna IP.
 */
@Component
public class IpsPermitidasAdmin {

    public static final String VARIABLE = "ADMIN_IPS_PERMITIDAS";

    // Solo literales: un nombre de host haría que InetAddress consulte el DNS al arrancar o en cada solicitud.
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");
    private static final Pattern MASCARA = Pattern.compile("^\\d{1,3}$");
    private static final int OCTETO_MAXIMO = 255;

    private final List<IpAddressMatcher> permitidas;

    public IpsPermitidasAdmin(@Value("${espacioya.admin.ips-permitidas}") String lista) {
        var matchers = new ArrayList<IpAddressMatcher>();
        if (lista != null && !lista.isBlank()) {
            var entradas = lista.split(",", -1);
            for (int i = 0; i < entradas.length; i++) {
                matchers.add(matcher(entradas[i].strip(), i + 1));
            }
        }
        this.permitidas = List.copyOf(matchers);
    }

    public boolean permite(String ip) {
        if (ip == null || !esDireccion(ip)) {
            return false;
        }
        return permitidas.stream().anyMatch(permitida -> permitida.matches(ip));
    }

    private static IpAddressMatcher matcher(String entrada, int posicion) {
        var partes = entrada.split("/", -1);
        var forma = partes.length <= 2 && esDireccion(partes[0])
                && (partes.length == 1 || MASCARA.matcher(partes[1]).matches());
        if (forma) {
            try {
                return new IpAddressMatcher(entrada);
            } catch (IllegalArgumentException e) {
                // Máscara mayor que la dirección (p. ej. /99) o literal IPv6 mal formado: se informa abajo.
            }
        }
        // El valor no va al mensaje: la configuración de seguridad no se escribe en los logs.
        throw new IllegalStateException("La entrada " + posicion + " de " + VARIABLE
                + " no es una dirección IP ni un rango CIDR válido");
    }

    private static boolean esDireccion(String texto) {
        var ipv4 = IPV4.matcher(texto);
        if (ipv4.matches()) {
            for (int grupo = 1; grupo <= 4; grupo++) {
                if (Integer.parseInt(ipv4.group(grupo)) > OCTETO_MAXIMO) {
                    return false;
                }
            }
            return true;
        }
        return IPV6.matcher(texto).matches();
    }
}
