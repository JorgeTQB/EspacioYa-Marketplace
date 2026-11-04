package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchivosPublicosTest {

    private static final byte[] FOTO = ArchivosDePrueba.png(20, 10);
    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private final AlmacenamientoFalso almacenamiento = new AlmacenamientoFalso();
    private final ArchivosPublicos archivos = new ArchivosPublicos(almacenamiento);

    @Test
    @DisplayName("HU-07 R33, R37: guarda los bytes sin transformar bajo <carpeta>/<UUID> aleatorio")
    void guardaSinCifrar() {
        var clave = archivos.guardar("espacios", FOTO);
        var otra = archivos.guardar("espacios", FOTO);

        assertThat(clave).matches("^espacios/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(otra).isNotEqualTo(clave);
        assertThat(almacenamiento.archivos.get(clave)).isEqualTo(FOTO);
    }

    @Test
    @DisplayName("HU-07 R37: una carpeta vacía, con mayúsculas o con / se rechaza sin guardar")
    void carpetaInvalida() {
        for (var carpeta : new String[] {"", "Espacios", "../espacios", "espacios/x", null}) {
            assertThatThrownBy(() -> archivos.guardar(carpeta, FOTO)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(almacenamiento.llamadas).isZero();
    }

    @Test
    @DisplayName("HU-07 R39, R54: leer devuelve los bytes guardados y vacío si la clave válida no existe")
    void leeLoGuardado() {
        var clave = archivos.guardar("espacios", FOTO);

        assertThat(archivos.leer(clave)).contains(FOTO);
        assertThat(archivos.leer("espacios/" + UUID)).isEmpty();
    }

    @Test
    @DisplayName("HU-07 R35: eliminar borra el archivo de una clave válida")
    void eliminaLoGuardado() {
        var clave = archivos.guardar("espacios", FOTO);

        archivos.eliminar(clave);

        assertThat(almacenamiento.archivos).doesNotContainKey(clave);
    }

    @Test
    @DisplayName("HU-07 R39, R54: leer y eliminar con una clave mal formada no tocan el adaptador")
    void claveMalFormadaNoTocaElAdaptador() {
        for (var clave : new String[] {"../x", "../espacios/" + UUID, "/espacios/" + UUID,
                "espacios/" + UUID.toUpperCase(), "espacios/..", "espacios", "", null}) {
            assertThat(archivos.leer(clave)).isEmpty();
            archivos.eliminar(clave);
        }
        assertThat(almacenamiento.llamadas).isZero();
    }

    private static final class AlmacenamientoFalso implements AlmacenamientoArchivos {

        private final Map<String, byte[]> archivos = new HashMap<>();
        private int llamadas;

        @Override
        public void guardar(String clave, byte[] contenido) {
            llamadas++;
            archivos.put(clave, contenido.clone());
        }

        @Override
        public Optional<byte[]> leer(String clave) {
            llamadas++;
            return Optional.ofNullable(archivos.get(clave));
        }

        @Override
        public void eliminar(String clave) {
            llamadas++;
            archivos.remove(clave);
        }
    }
}
