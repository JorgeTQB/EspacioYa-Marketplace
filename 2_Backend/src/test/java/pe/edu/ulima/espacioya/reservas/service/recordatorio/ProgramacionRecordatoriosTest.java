package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class ProgramacionRecordatoriosTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class, ProgramacionRecordatorios.class)
            .withPropertyValues("espacioya.reservas.recordatorios.intervalo=PT5M");

    @Configuration(proxyBeanMethods = false)
    static class Configuracion {

        @Bean
        RecordatoriosReserva recordatoriosReserva() {
            return mock(RecordatoriosReserva.class);
        }
    }

    @Test
    @DisplayName("HU-25 R26: con activa=true existe el proceso programado y habilita @Scheduled por sí mismo")
    void activo() {
        contexto.withPropertyValues("espacioya.reservas.recordatorios.activa=true").run(arrancado -> {
            assertThat(arrancado).hasSingleBean(ProgramacionRecordatorios.class);
            assertThat(arrancado).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    @DisplayName("HU-25 R26: con activa=false no se programa")
    void apagado() {
        contexto.withPropertyValues("espacioya.reservas.recordatorios.activa=false").run(arrancado -> {
            assertThat(arrancado).doesNotHaveBean(ProgramacionRecordatorios.class);
            assertThat(arrancado).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    @DisplayName("HU-25 R26: cada pasada delega en enviarPendientes")
    void delega() {
        var recordatorios = mock(RecordatoriosReserva.class);

        new ProgramacionRecordatorios(recordatorios).enviarRecordatorios();

        verify(recordatorios).enviarPendientes();
    }
}
