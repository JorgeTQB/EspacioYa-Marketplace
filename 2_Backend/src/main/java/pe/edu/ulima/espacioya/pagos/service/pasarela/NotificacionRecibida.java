package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.util.Map;
import java.util.TreeMap;

/** Parámetros de URL y encabezados de una notificación. El cuerpo no se lee: la firma no lo cubre (HU-13 DT-14). */
public record NotificacionRecibida(Map<String, String> parametros, Map<String, String> encabezados) {

    public NotificacionRecibida {
        var sinMayusculas = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        sinMayusculas.putAll(encabezados);
        encabezados = sinMayusculas;
    }
}
