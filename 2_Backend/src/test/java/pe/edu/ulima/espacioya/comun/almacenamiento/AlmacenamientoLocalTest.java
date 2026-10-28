package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlmacenamientoLocalTest {

    private static final String CLAVE = "identidad/0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final byte[] CONTENIDO = "bytes cifrados".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directorio;

    @Test
    @DisplayName("HU-05 R31: guarda el archivo en el directorio configurado, fuera de la base de datos, y lo lee")
    void guardaYLee() throws IOException {
        var almacenamiento = new AlmacenamientoLocal(directorio);

        almacenamiento.guardar(CLAVE, CONTENIDO);

        assertThat(Files.readAllBytes(directorio.resolve(CLAVE))).isEqualTo(CONTENIDO);
        assertThat(almacenamiento.leer(CLAVE)).contains(CONTENIDO);
        try (var archivos = Files.list(directorio.resolve("identidad"))) {
            assertThat(archivos).hasSize(1);
        }
    }

    @Test
    @DisplayName("HU-05 R31: leer una clave inexistente devuelve vacío")
    void leerInexistente() {
        assertThat(new AlmacenamientoLocal(directorio).leer(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("HU-05 R5: eliminar borra el archivo y es idempotente")
    void eliminarIdempotente() {
        var almacenamiento = new AlmacenamientoLocal(directorio);
        almacenamiento.guardar(CLAVE, CONTENIDO);

        almacenamiento.eliminar(CLAVE);
        almacenamiento.eliminar(CLAVE);

        assertThat(Files.exists(directorio.resolve(CLAVE))).isFalse();
        assertThat(almacenamiento.leer(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("HU-05 R27: una clave que sale del directorio se rechaza")
    void claveFueraDelDirectorio() {
        var almacenamiento = new AlmacenamientoLocal(directorio.resolve("raiz"));

        assertThatThrownBy(() -> almacenamiento.guardar("../fuera", CONTENIDO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(directorio.resolve("fuera"))).isFalse();
    }
}
