package pe.edu.ulima.espacioya.comun.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;

class ArchivoMultipartTest {

    @Test
    @DisplayName("HU-07 R28: una parte ausente o de 0 bytes se recibe sin contenido y con tamaño 0")
    void parteAusenteOVacia() {
        var vacia = new MockMultipartFile("foto", "x.jpg", "image/jpeg", new byte[0]);

        for (var archivo : List.of(ArchivoMultipart.recibido(null), ArchivoMultipart.recibido(vacia))) {
            assertThat(archivo.contenido()).isNull();
            assertThat(archivo.tamano()).isZero();
        }
    }

    @Test
    @DisplayName("HU-07 R29: una parte mayor al máximo se recibe con su tamaño y sin leer sus bytes")
    void parteDemasiadoGrandeNoSeLee() throws IOException {
        var grande = mock(MultipartFile.class);
        when(grande.isEmpty()).thenReturn(false);
        when(grande.getSize()).thenReturn(ValidadorArchivo.TAMANO_MAXIMO + 1);

        var recibido = ArchivoMultipart.recibido(grande);

        assertThat(recibido.contenido()).isNull();
        assertThat(recibido.tamano()).isEqualTo(ValidadorArchivo.TAMANO_MAXIMO + 1);
        verify(grande, never()).getBytes();
    }

    @Test
    @DisplayName("HU-07 R28, R29: una parte dentro del límite se recibe con sus bytes y su tamaño")
    void parteNormal() {
        var bytes = "contenido de la foto".getBytes(StandardCharsets.UTF_8);

        var recibido = ArchivoMultipart.recibido(new MockMultipartFile("foto", "x.png", "image/png", bytes));

        assertThat(recibido.contenido()).isEqualTo(bytes);
        assertThat(recibido.tamano()).isEqualTo(bytes.length);
    }
}
