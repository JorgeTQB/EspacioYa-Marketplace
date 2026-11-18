package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Crea el Adaptee {@link S3Client} de {@link AlmacenamientoS3} (HU-29), solo con
 * {@code espacioya.almacenamiento.tipo=s3}.
 * Las credenciales salen de la cadena por defecto del SDK (rol de la tarea en Fargate); nunca de la configuración.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = SeleccionAlmacenamiento.PROPIEDAD, havingValue = SeleccionAlmacenamiento.S3)
public class ConfiguracionAlmacenamientoS3 {

    // Sin límite, un S3 colgado retendría hilos de Tomcat sin fin; una subida de 5 MiB en la región cabe con holgura.
    static final Duration TIEMPO_INTENTO = Duration.ofSeconds(10);
    static final Duration TIEMPO_OPERACION = Duration.ofSeconds(30);

    @Bean(destroyMethod = "close")
    public S3Client clienteS3(@Value("${espacioya.almacenamiento.s3.region:}") String region) {
        return crearCliente(region);
    }

    // Construir el cliente no contacta a AWS ni resuelve credenciales.
    static S3Client crearCliente(String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("Falta ALMACENAMIENTO_S3_REGION (espacioya.almacenamiento.s3.region): "
                    + "el almacenamiento s3 la necesita");
        }
        return S3Client.builder()
                .region(Region.of(region.strip()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(c -> c.apiCallAttemptTimeout(TIEMPO_INTENTO).apiCallTimeout(TIEMPO_OPERACION))
                .build();
    }
}
