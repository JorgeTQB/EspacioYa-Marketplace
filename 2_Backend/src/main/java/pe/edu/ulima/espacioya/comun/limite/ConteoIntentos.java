package pe.edu.ulima.espacioya.comun.limite;

import java.time.Duration;

/** Almacén de los intentos detrás de {@link LimitadorIntentos}; mismas firmas y semántica (HU-30 D2). */
public interface ConteoIntentos {

    boolean intentar(String clave, int maximo, Duration ventana);

    boolean superaLimite(String clave, int maximo, Duration ventana);

    void registrar(String clave);

    void limpiar(String clave);
}
