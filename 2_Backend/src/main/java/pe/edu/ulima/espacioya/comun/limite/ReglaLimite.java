package pe.edu.ulima.espacioya.comun.limite;

import java.time.Duration;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Límite de intentos por IP para una ruta o un patrón de ruta ({@code /api/espacios/{id}}, HU-10 §9.9). Las reglas
 * con el mismo {@code grupo} comparten el contador (p. ej. los dos registros de cuentas suman un solo cupo por IP).
 */
public final class ReglaLimite {

    private final String metodo;
    private final String ruta;
    private final int maximo;
    private final Duration ventana;
    private final String grupo;
    private final PathPattern patron;

    public ReglaLimite(String metodo, String ruta, int maximo, Duration ventana, String grupo) {
        this.metodo = metodo;
        this.ruta = ruta;
        this.maximo = maximo;
        this.ventana = ventana;
        this.grupo = grupo;
        this.patron = PathPatternParser.defaultInstance.parse(ruta);
    }

    public ReglaLimite(String metodo, String ruta, int maximo, Duration ventana) {
        this(metodo, ruta, maximo, ventana, metodo + " " + ruta);
    }

    public String metodo() {
        return metodo;
    }

    public String ruta() {
        return ruta;
    }

    public int maximo() {
        return maximo;
    }

    public Duration ventana() {
        return ventana;
    }

    public String grupo() {
        return grupo;
    }

    public boolean aplicaA(String metodoSolicitud, String rutaSolicitud) {
        if (!metodo.equalsIgnoreCase(metodoSolicitud)) {
            return false;
        }
        // PathContainer compara los segmentos decodificados, como Spring MVC al elegir el controlador:
        // /api/%65spacios no esquiva el límite de /api/espacios (HU-10 R44).
        try {
            return patron.matches(PathContainer.parsePath(rutaSolicitud));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String clave(String ip) {
        return grupo + "|" + ip;
    }

    @Override
    public String toString() {
        return "ReglaLimite[" + metodo + " " + ruta + ", grupo=" + grupo + "]";
    }
}
