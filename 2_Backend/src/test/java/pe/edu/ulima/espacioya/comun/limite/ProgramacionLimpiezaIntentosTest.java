package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class ProgramacionLimpiezaIntentosTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ProgramacionLimpiezaIntentos.class)
            .withBean(ConteoIntentosPostgres.class, () -> mock(ConteoIntentosPostgres.class));

    @Test
    @DisplayName("HU-30 R26: con la limpieza activa se registra el proceso y ejecuta eliminarVencidos")
    void activaRegistraYEjecuta() {
        contexto.withPropertyValues("espacioya.limite.limpieza.activa=true",
                        "espacioya.limite.limpieza.intervalo=PT1H")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ProgramacionLimpiezaIntentos.class);
                    var conteo = ctx.getBean(ConteoIntentosPostgres.class);
                    verify(conteo, timeout(5_000).atLeastOnce()).eliminarVencidos();
                });
    }

    @Test
    @DisplayName("HU-30 R26: el proceso delega en eliminarVencidos con el retraso fijo del intervalo")
    void retrasoFijoDeLaPropiedad() throws NoSuchMethodException {
        var conteo = mock(ConteoIntentosPostgres.class);
        var metodo = ProgramacionLimpiezaIntentos.class.getMethod("eliminarVencidos");

        new ProgramacionLimpiezaIntentos(conteo).eliminarVencidos();

        verify(conteo, atLeastOnce()).eliminarVencidos();
        assertThat(metodo.getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${espacioya.limite.limpieza.intervalo}");
    }

    @Test
    @DisplayName("HU-30 R27: con la limpieza apagada no se registra el proceso")
    void apagadaNoRegistra() {
        contexto.withPropertyValues("espacioya.limite.limpieza.activa=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ProgramacionLimpiezaIntentos.class));
    }

    @Test
    @DisplayName("HU-30 R27: sin la propiedad de la limpieza no se registra el proceso")
    void sinPropiedadNoRegistra() {
        contexto.run(ctx -> assertThat(ctx).doesNotHaveBean(ProgramacionLimpiezaIntentos.class));
    }
}
