package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Proceso programado del recordatorio 24 h antes (HU-25 D1, R26; cierra §9 punto 8). {@code fixedDelay}: dos pasadas
 * nunca se solapan. Lleva su propio {@code @EnableScheduling}: el de {@code ProgramacionExpiracion} se apaga con su
 * propiedad. En las pruebas está apagado; ellas llaman a {@link RecordatoriosReserva#enviarPendientes()}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "espacioya.reservas.recordatorios.activa", havingValue = "true")
public class ProgramacionRecordatorios {

    private final RecordatoriosReserva recordatorios;

    public ProgramacionRecordatorios(RecordatoriosReserva recordatorios) {
        this.recordatorios = recordatorios;
    }

    @Scheduled(fixedDelayString = "${espacioya.reservas.recordatorios.intervalo}")
    public void enviarRecordatorios() {
        recordatorios.enviarPendientes();
    }
}
