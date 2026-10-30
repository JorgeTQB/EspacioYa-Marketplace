package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class ProgramacionExpiracionTest {

    private final ExpiracionReservas doble = mock(ExpiracionReservas.class);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(ExpiracionReservas.class, () -> doble)
            .withUserConfiguration(ProgramacionExpiracion.class);

    @Test
    @DisplayName("HU-12 R13: con activa = true ejecuta el proceso de expiración cada intervalo")
    void activaEjecutaElProceso() {
        contexto.withPropertyValues("espacioya.reservas.expiracion.activa=true",
                        "espacioya.reservas.expiracion.intervalo=PT0.05S")
                .run(listo -> {
                    assertThat(listo).hasSingleBean(ProgramacionExpiracion.class);
                    verify(doble, timeout(2000).atLeast(2)).expirarVencidas();
                });
    }

    @Test
    @DisplayName("HU-12 R14: con activa = false no se programa nada")
    void inactivaNoPrograma() {
        contexto.withPropertyValues("espacioya.reservas.expiracion.activa=false")
                .run(listo -> assertThat(listo).doesNotHaveBean(ProgramacionExpiracion.class));
    }

    @Test
    @DisplayName("HU-12 R13: por defecto está activo y el intervalo es de 1 minuto")
    void valoresPorDefecto() throws IOException {
        var propiedades = new Properties();
        try (var entrada = new ClassPathResource("application.properties").getInputStream()) {
            propiedades.load(entrada);
        }

        assertThat(propiedades.getProperty("espacioya.reservas.expiracion.activa"))
                .isEqualTo("${RESERVAS_EXPIRACION_ACTIVA:true}");
        assertThat(propiedades.getProperty("espacioya.reservas.expiracion.intervalo"))
                .isEqualTo("${RESERVAS_EXPIRACION_INTERVALO:PT1M}");
    }
}
