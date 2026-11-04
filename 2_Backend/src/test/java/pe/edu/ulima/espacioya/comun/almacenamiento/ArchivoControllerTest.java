package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ArchivoControllerTest {

    private static final String CLAVE = "identidad/0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String MIME = "application/pdf";
    private static final long EXPIRA = 1_793_200_000L;
    private static final String FIRMA = "firma";

    @Mock
    private FirmadorUrl firmador;
    @Mock
    private ArchivosPrivados archivos;

    private ArchivoController controlador;

    @BeforeEach
    void preparar() {
        controlador = new ArchivoController(firmador, archivos);
        when(firmador.esValida(CLAVE, MIME, EXPIRA, FIRMA)).thenReturn(true);
    }

    @Test
    @DisplayName("HU-06 R44: un error de E/S al leer un archivo con firma válida responde el 404 genérico sin la clave")
    void errorDeLecturaDa404(CapturedOutput salida) {
        when(archivos.leer(CLAVE)).thenThrow(new UncheckedIOException("No se pudo leer el archivo",
                new IOException("disco")));

        assertThatThrownBy(() -> controlador.servir(CLAVE, MIME, EXPIRA, FIRMA))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No encontramos lo que buscas o el enlace expiró.");
        assertThat(salida.getAll()).contains("no se pudo leer del almacenamiento").doesNotContain(CLAVE);
    }

    @Test
    @DisplayName("HU-06 R44: con la lectura correcta sigue sirviendo el archivo con no-store")
    void lecturaCorrectaSirveElArchivo() {
        when(archivos.leer(CLAVE)).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        var respuesta = controlador.servir(CLAVE, MIME, EXPIRA, FIRMA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).containsExactly(1, 2, 3);
        assertThat(respuesta.getHeaders().getCacheControl()).contains("no-store");
    }
}
