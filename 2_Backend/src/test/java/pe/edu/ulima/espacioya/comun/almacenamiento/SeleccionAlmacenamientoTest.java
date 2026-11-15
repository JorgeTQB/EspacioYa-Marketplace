package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

class SeleccionAlmacenamientoTest {

    private static final String DIRECTORIO = "espacioya.almacenamiento.directorio=almacenamiento-prueba";
    private static final String BUCKET = "espacioya.almacenamiento.s3.bucket=espacioya-uat-archivos";

    /** Con todas las piezas reales, también la configuración que crea el cliente S3. */
    private final ApplicationContextRunner completo = new ApplicationContextRunner()
            .withUserConfiguration(SeleccionAlmacenamiento.class, AlmacenamientoLocal.class, AlmacenamientoS3.class,
                    ConfiguracionAlmacenamientoS3.class)
            .withPropertyValues(DIRECTORIO);

    /** Con s3, el cliente real se reemplaza por uno simulado (sin ConfiguracionAlmacenamientoS3). */
    private ApplicationContextRunner conClienteSimulado(S3Client cliente) {
        return new ApplicationContextRunner()
                .withUserConfiguration(SeleccionAlmacenamiento.class, AlmacenamientoLocal.class, AlmacenamientoS3.class)
                .withBean(S3Client.class, () -> cliente)
                .withPropertyValues(DIRECTORIO);
    }

    @Test
    @DisplayName("HU-29 R1: sin tipo ni propiedades s3.* arranca con el adaptador local como único almacenamiento")
    void sinTipoUsaElDisco() {
        completo.run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto).hasSingleBean(AlmacenamientoArchivos.class);
            assertThat(contexto.getBean(AlmacenamientoArchivos.class)).isInstanceOf(AlmacenamientoLocal.class);
        });
    }

    @Test
    @DisplayName("HU-29 R2: con tipo local (sin distinguir mayúsculas) arranca con el adaptador local como único")
    void tipoLocalUsaElDisco() {
        for (var tipo : new String[] {"local", "LOCAL"}) {
            completo.withPropertyValues(SeleccionAlmacenamiento.PROPIEDAD + "=" + tipo).run(contexto -> {
                assertThat(contexto).hasNotFailed();
                assertThat(contexto).hasSingleBean(AlmacenamientoArchivos.class);
                assertThat(contexto).hasSingleBean(AlmacenamientoLocal.class);
            });
        }
    }

    @Test
    @DisplayName("HU-29 R3: sin tipo o con local no se crea ningún cliente S3 ni el adaptador S3")
    void sinClienteS3EnLocal() {
        completo.run(contexto -> {
            assertThat(contexto).doesNotHaveBean(S3Client.class);
            assertThat(contexto).doesNotHaveBean(AlmacenamientoS3.class);
        });
        completo.withPropertyValues(SeleccionAlmacenamiento.PROPIEDAD + "=local").run(contexto -> {
            assertThat(contexto).doesNotHaveBean(S3Client.class);
            assertThat(contexto).doesNotHaveBean(ConfiguracionAlmacenamientoS3.class);
        });
    }

    @Test
    @DisplayName("HU-29 R4: con tipo s3 (sin distinguir mayúsculas) el adaptador S3 es el único almacenamiento")
    void tipoS3UsaElBucket() {
        for (var tipo : new String[] {"s3", "S3"}) {
            conClienteSimulado(mock(S3Client.class))
                    .withPropertyValues(SeleccionAlmacenamiento.PROPIEDAD + "=" + tipo, BUCKET)
                    .run(contexto -> {
                        assertThat(contexto).hasNotFailed();
                        assertThat(contexto).hasSingleBean(AlmacenamientoArchivos.class);
                        assertThat(contexto.getBean(AlmacenamientoArchivos.class))
                                .isInstanceOf(AlmacenamientoS3.class);
                        assertThat(contexto).doesNotHaveBean(AlmacenamientoLocal.class);
                    });
        }
    }

    @Test
    @DisplayName("HU-29 R5: con otro valor (disco, vacío, ' s3') falla nombrando propiedad, variable, valor y válidos")
    void tipoInvalidoFalla() {
        for (var tipo : new String[] {"disco", "", " s3"}) {
            completo.withInitializer(contexto -> contexto.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("prueba", Map.of(SeleccionAlmacenamiento.PROPIEDAD, tipo))))
                    .run(contexto -> {
                        assertThat(contexto).hasFailed();
                        // Sale antes de crear cualquier bean: no viene envuelta en una BeanCreationException.
                        assertThat(contexto.getStartupFailure())
                                .isExactlyInstanceOf(IllegalStateException.class)
                                .hasMessage("espacioya.almacenamiento.tipo (ALMACENAMIENTO_TIPO) vale '" + tipo
                                        + "': los valores válidos son local y s3");
                    });
        }
        assertThatCode(() -> SeleccionAlmacenamiento.validar(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HU-29 R13: con s3 y sin bucket o con el bucket inexistente no arranca, tampoco con el disco")
    void s3QueFallaNoPasaAlDisco() {
        conClienteSimulado(mock(S3Client.class))
                .withPropertyValues(SeleccionAlmacenamiento.PROPIEDAD + "=s3")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("ALMACENAMIENTO_S3_BUCKET");
                });

        var cliente = mock(S3Client.class);
        when(cliente.headBucket(any(HeadBucketRequest.class)))
                .thenThrow((NoSuchBucketException) NoSuchBucketException.builder().statusCode(404).build());
        conClienteSimulado(cliente)
                .withPropertyValues(SeleccionAlmacenamiento.PROPIEDAD + "=s3", BUCKET)
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasStackTraceContaining("no existe");
                });

        completo.withPropertyValues(SeleccionAlmacenamiento.PROPIEDAD + "=s3", BUCKET).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure()).rootCause().hasMessageContaining("ALMACENAMIENTO_S3_REGION");
        });
    }
}
