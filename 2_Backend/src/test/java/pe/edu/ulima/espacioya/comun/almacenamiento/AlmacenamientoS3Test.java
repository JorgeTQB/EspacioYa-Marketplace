package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.cifrado.CifradorArchivos;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@ExtendWith(MockitoExtension.class)
class AlmacenamientoS3Test {

    private static final String BUCKET = "espacioya-uat-archivos";
    private static final String CLAVE = "identidad/0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String TEXTO_PROVEEDOR = "Detalle interno del proveedor RequestId 7F3A";
    private static final byte[] CONTENIDO = {0, 1, 2, (byte) 0xFF, 37, 80, 68, 70};

    @Mock
    private S3Client cliente;

    private AlmacenamientoS3 adaptador() {
        var adaptador = new AlmacenamientoS3(cliente, BUCKET);
        clearInvocations(cliente);
        return adaptador;
    }

    private static S3Exception falla(int estado, String codigo) {
        return (S3Exception) S3Exception.builder()
                .statusCode(estado)
                .message(TEXTO_PROVEEDOR + " " + BUCKET + " " + CLAVE)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(codigo).errorMessage(TEXTO_PROVEEDOR).build())
                .build();
    }

    private static IllegalStateException fallaDeArranque(S3Client cliente, String bucket) {
        return catchThrowableOfType(IllegalStateException.class, () -> new AlmacenamientoS3(cliente, bucket));
    }

    private static byte[] cuerpo(RequestBody cuerpo) throws IOException {
        try (var flujo = cuerpo.contentStreamProvider().newStream()) {
            return flujo.readAllBytes();
        }
    }

    private RequestBody cuerpoEnviado() {
        var cuerpo = ArgumentCaptor.forClass(RequestBody.class);
        verify(cliente).putObject(any(PutObjectRequest.class), cuerpo.capture());
        return cuerpo.getValue();
    }

    private PutObjectRequest pedidoEnviado() {
        var pedido = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cliente).putObject(pedido.capture(), any(RequestBody.class));
        return pedido.getValue();
    }

    @Test
    @DisplayName("HU-29 R6: sin bucket o con bucket en blanco no arranca, nombra la propiedad y no llama a S3")
    void sinBucketNoArranca() {
        for (var bucket : new String[] {null, "", "   "}) {
            var error = fallaDeArranque(cliente, bucket);

            assertThat(error.getMessage())
                    .contains("espacioya.almacenamiento.s3.bucket")
                    .contains("ALMACENAMIENTO_S3_BUCKET");
        }
        verifyNoInteractions(cliente);
    }

    @Test
    @DisplayName("HU-29 R8: al construirse comprueba el bucket configurado con HeadBucket")
    void compruebaElBucketAlArrancar() {
        new AlmacenamientoS3(cliente, " " + BUCKET + " ");

        verify(cliente).headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    @DisplayName("HU-29 R9: si el bucket no existe (NoSuchBucket o 404) el arranque falla diciendo que no existe")
    void bucketInexistente() {
        var noExiste = (NoSuchBucketException) NoSuchBucketException.builder().statusCode(404).build();
        when(cliente.headBucket(any(HeadBucketRequest.class))).thenThrow(noExiste, falla(404, "NotFound"));

        for (var intento = 0; intento < 2; intento++) {
            var error = fallaDeArranque(cliente, BUCKET);

            assertThat(error.getMessage()).contains("'" + BUCKET + "'").contains("no existe");
        }
    }

    @Test
    @DisplayName("HU-29 R10: si HeadBucket responde 403 el arranque falla diciendo que no es accesible con el rol")
    void bucketInaccesible() {
        when(cliente.headBucket(any(HeadBucketRequest.class))).thenThrow(falla(403, "Forbidden"));

        var error = fallaDeArranque(cliente, BUCKET);

        assertThat(error.getMessage())
                .contains("'" + BUCKET + "'")
                .contains("no es accesible con las credenciales del rol");
    }

    @Test
    @DisplayName("HU-29 R11: si HeadBucket responde 301 el arranque falla nombrando ALMACENAMIENTO_S3_REGION")
    void bucketEnOtraRegion() {
        when(cliente.headBucket(any(HeadBucketRequest.class))).thenThrow(falla(301, "PermanentRedirect"));

        var error = fallaDeArranque(cliente, BUCKET);

        assertThat(error.getMessage()).contains("'" + BUCKET + "'").contains("ALMACENAMIENTO_S3_REGION");
    }

    @Test
    @DisplayName("HU-29 R12: otra falla al comprobar (sin credenciales, red, otro código) hace fallar el arranque")
    void otraFallaAlComprobar() {
        when(cliente.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials from any of the providers"),
                        falla(500, "InternalError"));

        var sinCredenciales = fallaDeArranque(cliente, BUCKET);
        var errorInterno = fallaDeArranque(cliente, BUCKET);

        assertThat(sinCredenciales.getMessage()).contains("'" + BUCKET + "'").contains("SdkClientException");
        assertThat(errorInterno.getMessage()).contains("'" + BUCKET + "'").contains("500");
    }

    @Test
    @DisplayName("HU-29 R14: guardar envía un PutObject al bucket con llave = clave y el cuerpo exacto")
    void guardarEnviaPutObject() throws IOException {
        adaptador().guardar(CLAVE, CONTENIDO);

        var pedido = ArgumentCaptor.forClass(PutObjectRequest.class);
        var cuerpo = ArgumentCaptor.forClass(RequestBody.class);
        verify(cliente).putObject(pedido.capture(), cuerpo.capture());
        assertThat(pedido.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(pedido.getValue().key()).isEqualTo(CLAVE);
        assertThat(cuerpo(cuerpo.getValue())).isEqualTo(CONTENIDO);
    }

    @Test
    @DisplayName("HU-29 R15: cada PutObject pide cifrado en reposo SSE-S3 (AES256)")
    void guardarPideSseS3() {
        adaptador().guardar(CLAVE, CONTENIDO);

        assertThat(pedidoEnviado().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
    }

    @Test
    @DisplayName("HU-29 R16: el PutObject no asigna ACL al objeto")
    void guardarSinAcl() {
        adaptador().guardar(CLAVE, CONTENIDO);

        var pedido = pedidoEnviado();
        assertThat(pedido.acl()).isNull();
        assertThat(pedido.aclAsString()).isNull();
        assertThat(pedido.grantRead()).isNull();
        assertThat(pedido.grantFullControl()).isNull();
    }

    @Test
    @DisplayName("HU-29 R17: leer devuelve los bytes exactos del objeto con llave = clave en el bucket")
    void leerDevuelveLosBytes() {
        var pedido = GetObjectRequest.builder().bucket(BUCKET).key(CLAVE).build();
        when(cliente.getObjectAsBytes(pedido))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), CONTENIDO.clone()));

        assertThat(adaptador().leer(CLAVE)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(CONTENIDO));
    }

    @Test
    @DisplayName("HU-29 R18: si la llave no existe (NoSuchKey) leer devuelve vacío")
    void leerLlaveInexistente() {
        when(cliente.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow((NoSuchKeyException) NoSuchKeyException.builder().statusCode(404).build());

        assertThat(adaptador().leer(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("HU-29 R19: eliminar envía un DeleteObject al bucket con llave = clave")
    void eliminarEnviaDeleteObject() {
        adaptador().eliminar(CLAVE);

        verify(cliente).deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(CLAVE).build());
    }

    @Test
    @DisplayName("HU-29 R20: si PutObject falla (S3Exception o SdkClientException) lanza la excepción del disco")
    void guardarFalla() {
        when(cliente.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(falla(503, "SlowDown"), SdkClientException.create(TEXTO_PROVEEDOR));
        var adaptador = adaptador();

        for (var intento = 0; intento < 2; intento++) {
            assertThatThrownBy(() -> adaptador.guardar(CLAVE, CONTENIDO))
                    .isExactlyInstanceOf(UncheckedIOException.class)
                    .hasMessage("No se pudo guardar el archivo")
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Test
    @DisplayName("HU-29 R21: si GetObject falla por otro motivo (403, 500, red) lanza la del disco, no vacío")
    void leerFalla() {
        when(cliente.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(falla(403, "AccessDenied"), falla(500, "InternalError"),
                        SdkClientException.create(TEXTO_PROVEEDOR));
        var adaptador = adaptador();

        for (var intento = 0; intento < 3; intento++) {
            assertThatThrownBy(() -> adaptador.leer(CLAVE))
                    .isExactlyInstanceOf(UncheckedIOException.class)
                    .hasMessage("No se pudo leer el archivo");
        }
    }

    @Test
    @DisplayName("HU-29 R22: si DeleteObject falla lanza la excepción del disco")
    void eliminarFalla() {
        when(cliente.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(falla(500, "InternalError"), SdkClientException.create(TEXTO_PROVEEDOR));
        var adaptador = adaptador();

        for (var intento = 0; intento < 2; intento++) {
            assertThatThrownBy(() -> adaptador.eliminar(CLAVE))
                    .isExactlyInstanceOf(UncheckedIOException.class)
                    .hasMessage("No se pudo eliminar el archivo");
        }
    }

    @Test
    @DisplayName("HU-29 R23: la excepción de una falla no contiene la clave, el bucket ni el texto del proveedor")
    void fallaSinDetalles() {
        when(cliente.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(falla(503, "SlowDown"));
        when(cliente.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create(TEXTO_PROVEEDOR + " " + BUCKET + " " + CLAVE));
        when(cliente.deleteObject(any(DeleteObjectRequest.class))).thenThrow(falla(500, "InternalError"));
        var adaptador = adaptador();

        var errores = new UncheckedIOException[] {
            catchThrowableOfType(UncheckedIOException.class, () -> adaptador.guardar(CLAVE, CONTENIDO)),
            catchThrowableOfType(UncheckedIOException.class, () -> adaptador.leer(CLAVE)),
            catchThrowableOfType(UncheckedIOException.class, () -> adaptador.eliminar(CLAVE))};

        for (var error : errores) {
            assertThat(error.getCause()).isExactlyInstanceOf(IOException.class);
            assertThat(error.getCause().getCause()).isNull();
            for (var mensaje : new String[] {error.getMessage(), error.getCause().getMessage()}) {
                assertThat(mensaje).doesNotContain(CLAVE).doesNotContain(BUCKET).doesNotContain(TEXTO_PROVEEDOR)
                        .doesNotContain("RequestId");
            }
        }
        assertThat(errores[0].getCause().getMessage()).isEqualTo("S3Exception 503 SlowDown");
        assertThat(errores[1].getCause().getMessage()).isEqualTo("SdkClientException");
    }

    @Test
    @DisplayName("HU-29 R24: clave nula, en blanco, absoluta o con .. → IllegalArgumentException sin llamar a S3")
    void claveInvalidaNoLlegaAS3() {
        var adaptador = adaptador();

        for (var clave : new String[] {null, "", "  ", "/x", "../x", "a/../b", "a/.."}) {
            assertThatThrownBy(() -> adaptador.guardar(clave, CONTENIDO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> adaptador.leer(clave)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> adaptador.eliminar(clave)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(cliente);
    }

    @Test
    @DisplayName("HU-29 R26: con ArchivosPrivados el cuerpo del PutObject es el contenido cifrado, no el claro")
    void archivosPrivadosSubeCifrado() throws IOException {
        var claro = "%PDF-1.4 documento de identidad".getBytes(StandardCharsets.UTF_8);
        var cifrador = new CifradorArchivos(FirmadorUrlTest.LLAVE_CIFRADO);
        var firmador = new FirmadorUrl(FirmadorUrlTest.LLAVE_FIRMA, FirmadorUrlTest.LLAVE_CIFRADO,
                FirmadorUrlTest.LLAVE_HUELLA, Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC));
        var archivos = new ArchivosPrivados(adaptador(), cifrador, firmador);

        var clave = archivos.guardar("identidad", claro);

        assertThat(pedidoEnviado().key()).isEqualTo(clave);
        var subido = cuerpo(cuerpoEnviado());
        assertThat(Arrays.equals(subido, claro)).isFalse();
        assertThat(new String(subido, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
        assertThat(cifrador.descifrar(subido)).isEqualTo(claro);
    }
}
