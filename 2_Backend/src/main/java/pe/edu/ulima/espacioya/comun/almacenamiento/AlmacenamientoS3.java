package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * ★ Adapter (HU-29): {@link AlmacenamientoArchivos} sobre un bucket S3 privado, con
 * {@code espacioya.almacenamiento.tipo=s3}. Única clase, con {@link ConfiguracionAlmacenamientoS3}, que importa
 * {@code software.amazon.awssdk}. Usa las mismas claves que el disco, sin prefijo en el bucket.
 */
@Component
@ConditionalOnProperty(name = SeleccionAlmacenamiento.PROPIEDAD, havingValue = SeleccionAlmacenamiento.S3)
public class AlmacenamientoS3 implements AlmacenamientoArchivos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoS3.class);

    private static final int NO_ENCONTRADO = 404;
    private static final int PROHIBIDO = 403;
    private static final int MOVIDO_PERMANENTEMENTE = 301;

    private final S3Client cliente;
    private final String bucket;

    public AlmacenamientoS3(S3Client cliente, @Value("${espacioya.almacenamiento.s3.bucket:}") String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Falta ALMACENAMIENTO_S3_BUCKET (espacioya.almacenamiento.s3.bucket): "
                    + "el almacenamiento s3 la necesita");
        }
        this.cliente = cliente;
        this.bucket = bucket.strip();
        comprobarBucket();
        log.info("Almacenamiento de archivos en el bucket S3 {}", this.bucket);
    }

    @Override
    public void guardar(String clave, byte[] contenido) {
        validarClave(clave);
        // Sin ACL: el bucket es privado (Block Public Access); SSE-S3 se pide aunque el bucket ya cifre por defecto.
        var pedido = PutObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
        try {
            cliente.putObject(pedido, RequestBody.fromBytes(contenido));
        } catch (SdkException e) {
            throw FallaAlmacenamiento.alGuardar(new IOException(describir(e)));
        }
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        validarClave(clave);
        var pedido = GetObjectRequest.builder().bucket(bucket).key(clave).build();
        try {
            return Optional.of(cliente.getObjectAsBytes(pedido).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // Un 403 no es "no existe": HeadBucket exige s3:ListBucket, así que una llave inexistente siempre da 404.
            if (e.statusCode() == NO_ENCONTRADO) {
                return Optional.empty();
            }
            throw FallaAlmacenamiento.alLeer(new IOException(describir(e)));
        } catch (SdkException e) {
            throw FallaAlmacenamiento.alLeer(new IOException(describir(e)));
        }
    }

    @Override
    public void eliminar(String clave) {
        validarClave(clave);
        var pedido = DeleteObjectRequest.builder().bucket(bucket).key(clave).build();
        try {
            cliente.deleteObject(pedido);
        } catch (SdkException e) {
            throw FallaAlmacenamiento.alEliminar(new IOException(describir(e)));
        }
    }

    private void comprobarBucket() {
        try {
            cliente.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            throw noExiste(e);
        } catch (S3Exception e) {
            throw switch (e.statusCode()) {
                case NO_ENCONTRADO -> noExiste(e);
                case PROHIBIDO -> new IllegalStateException("El bucket S3 '" + bucket + "' no es accesible con las "
                        + "credenciales del rol: revisa sus permisos (s3:ListBucket, s3:GetObject, s3:PutObject, "
                        + "s3:DeleteObject)", e);
                case MOVIDO_PERMANENTEMENTE -> new IllegalStateException("El bucket S3 '" + bucket
                        + "' está en otra región: revisa ALMACENAMIENTO_S3_REGION", e);
                default -> noSeComprobo(e);
            };
        } catch (SdkException e) {
            throw noSeComprobo(e);
        }
    }

    private IllegalStateException noExiste(SdkException e) {
        return new IllegalStateException("El bucket S3 '" + bucket + "' no existe (ALMACENAMIENTO_S3_BUCKET). "
                + "La aplicación no crea el bucket", e);
    }

    private IllegalStateException noSeComprobo(SdkException e) {
        return new IllegalStateException(
                "No se pudo comprobar el bucket S3 '" + bucket + "' (" + describir(e) + ")", e);
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.isBlank() || clave.startsWith("/")) {
            throw claveFuera();
        }
        for (var segmento : clave.split("/", -1)) {
            if ("..".equals(segmento)) {
                throw claveFuera();
            }
        }
    }

    private static IllegalArgumentException claveFuera() {
        return new IllegalArgumentException("Clave de almacenamiento fuera del directorio");
    }

    // Solo el tipo, el código HTTP y el código de AWS: getMessage() del SDK trae el texto de S3 y el request id.
    private static String describir(SdkException e) {
        var tipo = e.getClass().getSimpleName();
        if (e instanceof AwsServiceException servicio) {
            var detalles = servicio.awsErrorDetails();
            var codigo = detalles == null || detalles.errorCode() == null ? "" : " " + detalles.errorCode();
            return tipo + " " + servicio.statusCode() + codigo;
        }
        return tipo;
    }
}
