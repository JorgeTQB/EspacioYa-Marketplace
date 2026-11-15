package pe.edu.ulima.espacioya.comun.limite;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Limpieza programada de {@code intento_limite} (HU-30 D8). Cada réplica la programa; el bloqueo consultivo de
 * {@link ConteoIntentosPostgres#eliminarVencidos()} evita pasadas dobles. {@code @EnableScheduling} propio porque los
 * otros procesos programados se apagan con sus propiedades. Se apaga en las pruebas.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "espacioya.limite.limpieza.activa", havingValue = "true")
public class ProgramacionLimpiezaIntentos {

    private final ConteoIntentosPostgres conteo;

    public ProgramacionLimpiezaIntentos(ConteoIntentosPostgres conteo) {
        this.conteo = conteo;
    }

    @Scheduled(fixedDelayString = "${espacioya.limite.limpieza.intervalo}")
    public void eliminarVencidos() {
        conteo.eliminarVencidos();
    }
}
