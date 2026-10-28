package pe.edu.ulima.espacioya.comun.limite;

import java.time.Duration;

/**
 * Límite de intentos por IP para una ruta. Las reglas con el mismo {@code grupo} comparten el
 * contador (p. ej. los dos registros de cuentas suman un solo cupo por IP).
 */
public record ReglaLimite(String metodo, String ruta, int maximo, Duration ventana, String grupo) {

    public ReglaLimite(String metodo, String ruta, int maximo, Duration ventana) {
        this(metodo, ruta, maximo, ventana, metodo + " " + ruta);
    }

    public boolean aplicaA(String metodoSolicitud, String rutaSolicitud) {
        return metodo.equalsIgnoreCase(metodoSolicitud) && ruta.equals(rutaSolicitud);
    }

    public String clave(String ip) {
        return grupo + "|" + ip;
    }
}
