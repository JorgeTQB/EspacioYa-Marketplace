package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato de {@link AlmacenamientoArchivos} que cumplen los dos adaptadores (HU-29 R25). No termina en {@code Test}:
 * corre a través de sus subclases.
 */
abstract class ContratoAlmacenamientoArchivos {

    private static final String CLAVE = "identidad/0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final byte[] CONTENIDO = {0, 1, 2, (byte) 0xFF, 37, 80, 68, 70};

    private AlmacenamientoArchivos almacenamiento;

    protected abstract AlmacenamientoArchivos crearAlmacenamiento();

    @BeforeEach
    void prepararAlmacenamiento() {
        almacenamiento = crearAlmacenamiento();
    }

    @Test
    @DisplayName("HU-29 R25: guardar y luego leer devuelve los mismos bytes")
    void guardarYLeer() {
        almacenamiento.guardar(CLAVE, CONTENIDO);

        assertThat(almacenamiento.leer(CLAVE)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(CONTENIDO));
    }

    @Test
    @DisplayName("HU-29 R25: guardar de nuevo la misma clave reemplaza el contenido")
    void sobrescribirReemplaza() {
        var nuevo = "segunda versión".getBytes(StandardCharsets.UTF_8);
        almacenamiento.guardar(CLAVE, CONTENIDO);

        almacenamiento.guardar(CLAVE, nuevo);

        assertThat(almacenamiento.leer(CLAVE)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(nuevo));
    }

    @Test
    @DisplayName("HU-29 R25, R18: leer una clave inexistente devuelve vacío")
    void leerInexistenteEsVacio() {
        assertThat(almacenamiento.leer(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("HU-29 R25: eliminar dos veces la misma clave no falla y la deja inexistente")
    void eliminarDosVeces() {
        almacenamiento.guardar(CLAVE, CONTENIDO);

        assertThatCode(() -> {
            almacenamiento.eliminar(CLAVE);
            almacenamiento.eliminar(CLAVE);
        }).doesNotThrowAnyException();
        assertThat(almacenamiento.leer(CLAVE)).isEmpty();
    }

    @Test
    @DisplayName("HU-29 R25: una clave con .. se rechaza con IllegalArgumentException")
    void claveFueraSeRechaza() {
        assertThatThrownBy(() -> almacenamiento.guardar("../fuera", CONTENIDO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacenamiento.leer("../fuera")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacenamiento.eliminar("../fuera")).isInstanceOf(IllegalArgumentException.class);
    }
}
