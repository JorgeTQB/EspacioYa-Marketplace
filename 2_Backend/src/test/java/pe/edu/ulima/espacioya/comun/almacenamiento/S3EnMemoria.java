package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** Doble de {@link S3Client} con un solo bucket en memoria (HU-29): las llaves se guardan tal cual. */
class S3EnMemoria implements S3Client {

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

    @Override
    public HeadBucketResponse headBucket(HeadBucketRequest pedido) {
        return HeadBucketResponse.builder().build();
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest pedido, RequestBody cuerpo) {
        try (var flujo = cuerpo.contentStreamProvider().newStream()) {
            objetos.put(pedido.key(), flujo.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return PutObjectResponse.builder().build();
    }

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest pedido) {
        var contenido = objetos.get(pedido.key());
        if (contenido == null) {
            throw (NoSuchKeyException) NoSuchKeyException.builder().statusCode(404).build();
        }
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), contenido.clone());
    }

    @Override
    public DeleteObjectResponse deleteObject(DeleteObjectRequest pedido) {
        objetos.remove(pedido.key());
        return DeleteObjectResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public void close() {
        objetos.clear();
    }
}
