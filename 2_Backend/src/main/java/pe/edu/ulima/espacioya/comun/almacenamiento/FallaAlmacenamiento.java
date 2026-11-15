package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Excepción común de los adaptadores de almacenamiento ante fallas del proveedor (HU-29 D4). */
final class FallaAlmacenamiento {

    static final String MENSAJE_GUARDAR = "No se pudo guardar el archivo";
    static final String MENSAJE_LEER = "No se pudo leer el archivo";
    static final String MENSAJE_ELIMINAR = "No se pudo eliminar el archivo";

    private FallaAlmacenamiento() {
    }

    static UncheckedIOException alGuardar(IOException causa) {
        return new UncheckedIOException(MENSAJE_GUARDAR, causa);
    }

    static UncheckedIOException alLeer(IOException causa) {
        return new UncheckedIOException(MENSAJE_LEER, causa);
    }

    static UncheckedIOException alEliminar(IOException causa) {
        return new UncheckedIOException(MENSAJE_ELIMINAR, causa);
    }
}
