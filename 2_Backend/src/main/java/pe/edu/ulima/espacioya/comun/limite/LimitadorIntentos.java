package pe.edu.ulima.espacioya.comun.limite;

import java.time.Clock;
import java.time.Duration;

/**
 * Límites de intentos por clave con ventana deslizante. El bean de la aplicación cuenta en PostgreSQL y vale para
 * todas las réplicas ({@link ConfiguracionLimiteIntentos}, HU-30); {@code new LimitadorIntentos(reloj)} cuenta en
 * memoria y es para las pruebas unitarias.
 */
public class LimitadorIntentos {

    private final ConteoIntentos conteo;

    public LimitadorIntentos(Clock reloj) {
        this(new ConteoIntentosMemoria(reloj));
    }

    public LimitadorIntentos(ConteoIntentos conteo) {
        this.conteo = conteo;
    }

    public boolean intentar(String clave, int maximo, Duration ventana) {
        return conteo.intentar(clave, maximo, ventana);
    }

    public boolean superaLimite(String clave, int maximo, Duration ventana) {
        return conteo.superaLimite(clave, maximo, ventana);
    }

    public void registrar(String clave) {
        conteo.registrar(clave);
    }

    public void limpiar(String clave) {
        conteo.limpiar(clave);
    }
}
