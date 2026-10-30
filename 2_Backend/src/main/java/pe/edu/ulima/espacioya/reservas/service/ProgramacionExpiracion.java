package pe.edu.ulima.espacioya.reservas.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Proceso programado de la expiración (HU-12 DT-6). {@code fixedDelay}: dos pasadas nunca se solapan. Se apaga en las
 * pruebas para no competir con sus limpiezas; ellas llaman a {@link ExpiracionReservas#expirarVencidas()}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "espacioya.reservas.expiracion.activa", havingValue = "true")
public class ProgramacionExpiracion {

    private final ExpiracionReservas expiracion;

    public ProgramacionExpiracion(ExpiracionReservas expiracion) {
        this.expiracion = expiracion;
    }

    @Scheduled(fixedDelayString = "${espacioya.reservas.expiracion.intervalo}")
    public void expirarVencidas() {
        expiracion.expirarVencidas();
    }
}
