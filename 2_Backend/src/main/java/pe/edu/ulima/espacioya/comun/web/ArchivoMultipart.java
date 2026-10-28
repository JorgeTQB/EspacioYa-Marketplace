package pe.edu.ulima.espacioya.comun.web;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.web.multipart.MultipartFile;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;

/**
 * Convierte una parte multipart en {@link ArchivoRecibido}. Vive en la capa web para que
 * {@code ArchivoRecibido} siga sin depender de Spring Web.
 */
public final class ArchivoMultipart {

    private ArchivoMultipart() {
    }

    // No se leen los bytes de un archivo que ya excede el límite: basta su tamaño para rechazarlo.
    public static ArchivoRecibido recibido(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return new ArchivoRecibido(null, 0);
        }
        if (archivo.getSize() > ValidadorArchivo.TAMANO_MAXIMO) {
            return new ArchivoRecibido(null, archivo.getSize());
        }
        try {
            return new ArchivoRecibido(archivo.getBytes(), archivo.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo recibido", e);
        }
    }
}
