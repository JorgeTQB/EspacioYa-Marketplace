package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class ConfiguracionAlmacenamientoS3Test {

    @Test
    @DisplayName("HU-29 R7: con la región vacía, en blanco o nula falla nombrando la propiedad y la variable")
    void sinRegionFalla() {
        for (var region : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> ConfiguracionAlmacenamientoS3.crearCliente(region))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("espacioya.almacenamiento.s3.region")
                    .hasMessageContaining("ALMACENAMIENTO_S3_REGION");
        }
    }

    @Test
    @DisplayName("HU-29 R27: el cliente toma las credenciales de la cadena por defecto del SDK")
    void credencialesDeLaCadenaPorDefecto() {
        try (var cliente = ConfiguracionAlmacenamientoS3.crearCliente("us-east-1")) {
            assertThat(cliente.serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(DefaultCredentialsProvider.class);
        }
    }

    @Test
    @DisplayName("HU-29 R29: el cliente usa la región configurada")
    void usaLaRegionConfigurada() {
        try (var cliente = ConfiguracionAlmacenamientoS3.crearCliente(" us-east-1 ")) {
            assertThat(cliente.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
        }
    }

    @Test
    @DisplayName("HU-29 R30: cada intento dura hasta 10 s y cada operación con reintentos hasta 30 s")
    void limitaLosTiempos() {
        try (var cliente = ConfiguracionAlmacenamientoS3.crearCliente("us-east-1")) {
            var configuracion = cliente.serviceClientConfiguration().overrideConfiguration();

            assertThat(configuracion.apiCallAttemptTimeout()).hasValue(ConfiguracionAlmacenamientoS3.TIEMPO_INTENTO);
            assertThat(configuracion.apiCallTimeout()).hasValue(ConfiguracionAlmacenamientoS3.TIEMPO_OPERACION);
            assertThat(ConfiguracionAlmacenamientoS3.TIEMPO_INTENTO).hasSeconds(10);
            assertThat(ConfiguracionAlmacenamientoS3.TIEMPO_OPERACION).hasSeconds(30);
        }
    }

    @Test
    @DisplayName("HU-29 R28: application.properties y .env.example no declaran claves de acceso de AWS")
    void sinClavesDeAccesoDeAws() throws IOException {
        for (var archivo : new Path[] {Path.of("src/main/resources/application.properties"), Path.of(".env.example")}) {
            var contenido = Files.readString(archivo, StandardCharsets.UTF_8);

            assertThat(contenido).contains("ALMACENAMIENTO_S3_BUCKET");
            assertThat(contenido.toLowerCase())
                    .doesNotContain("aws_access_key_id")
                    .doesNotContain("aws_secret_access_key")
                    .doesNotContain("aws_session_token")
                    .doesNotContain("aws.accesskeyid")
                    .doesNotContain("aws.secretaccesskey")
                    .doesNotContain("aws.sessiontoken");
        }
    }
}
