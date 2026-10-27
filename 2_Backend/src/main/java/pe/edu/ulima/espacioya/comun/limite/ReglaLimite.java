package pe.edu.ulima.espacioya.comun.limite;

import java.time.Duration;

public record ReglaLimite(String metodo, String ruta, int maximo, Duration ventana) {

    public boolean aplicaA(String metodoSolicitud, String rutaSolicitud) {
        return metodo.equalsIgnoreCase(metodoSolicitud) && ruta.equals(rutaSolicitud);
    }

    public String clave(String ip) {
        return metodo + " " + ruta + "|" + ip;
    }
}
