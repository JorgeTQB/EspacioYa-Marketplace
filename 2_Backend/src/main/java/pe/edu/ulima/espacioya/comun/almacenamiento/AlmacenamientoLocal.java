package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Archivos en un directorio local, fuera del repositorio y de la base de datos. En UAT el directorio
 * debe estar en un volumen persistente.
 */
@Component
public class AlmacenamientoLocal implements AlmacenamientoArchivos {

    private final Path directorio;

    public AlmacenamientoLocal(@Value("${espacioya.almacenamiento.directorio}") Path directorio) {
        this.directorio = directorio.toAbsolutePath().normalize();
    }

    @Override
    public void guardar(String clave, byte[] contenido) {
        var destino = ruta(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Escritura atómica: nunca queda a la vista un archivo a medio escribir.
            var temporal = Files.createTempFile(destino.getParent(), ".subida-", ".tmp");
            try {
                Files.write(temporal, contenido);
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        try {
            return Optional.of(Files.readAllBytes(ruta(clave)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo", e);
        }
    }

    @Override
    public void eliminar(String clave) {
        try {
            Files.deleteIfExists(ruta(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo eliminar el archivo", e);
        }
    }

    private Path ruta(String clave) {
        var ruta = directorio.resolve(clave).normalize();
        if (!ruta.startsWith(directorio) || ruta.equals(directorio)) {
            throw new IllegalArgumentException("Clave de almacenamiento fuera del directorio");
        }
        return ruta;
    }
}
