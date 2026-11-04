package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FotoEspacioTest {

    private static final String CLAVE = "espacios/0f8fad5b-d9cb-469f-a165-70867728950e";

    @Test
    @DisplayName("HU-07 R26: registrar guarda el espacio, la clave, el tipo MIME y el orden")
    void registrar() {
        var foto = FotoEspacio.registrar(3L, CLAVE, "image/png", 2);

        assertThat(foto.getEspacioId()).isEqualTo(3L);
        assertThat(foto.getRutaArchivo()).isEqualTo(CLAVE);
        assertThat(foto.getTipoMime()).isEqualTo("image/png");
        assertThat(foto.getOrden()).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-07 R38: registrar con un orden fuera de 1 a 5 lanza")
    void registrarFueraDeRango() {
        for (var orden : new int[] {0, 6, -1}) {
            assertThatThrownBy(() -> FotoEspacio.registrar(3L, CLAVE, "image/png", orden))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("HU-07 R34, R38: moverA cambia el orden dentro de 1 a 5 y lanza fuera de ese rango")
    void moverA() {
        var foto = FotoEspacio.registrar(3L, CLAVE, "image/jpeg", 3);

        foto.moverA(2);
        assertThat(foto.getOrden()).isEqualTo(2);

        assertThatThrownBy(() -> foto.moverA(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> foto.moverA(6)).isInstanceOf(IllegalArgumentException.class);
        assertThat(foto.getOrden()).isEqualTo(2);
    }
}
