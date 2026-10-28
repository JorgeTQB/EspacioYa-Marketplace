package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Archivos no sensibles guardados sin cifrar (fotos de espacios, HU-07 DT-18). Igual que
 * {@link ArchivosPrivados}, valida la carpeta y la forma de la clave antes de tocar el adaptador (sin path
 * traversal); a diferencia de él, no cifra ni firma URL: quien los sirve decide la visibilidad.
 */
@Service
public class ArchivosPublicos {

    private static final Pattern CARPETA = Pattern.compile("^[a-z]+$");

    private final AlmacenamientoArchivos almacenamiento;

    public ArchivosPublicos(AlmacenamientoArchivos almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    public String guardar(String carpeta, byte[] contenido) {
        if (carpeta == null || !CARPETA.matcher(carpeta).matches()) {
            throw new IllegalArgumentException("Carpeta de almacenamiento no válida");
        }
        var clave = carpeta + "/" + UUID.randomUUID();
        almacenamiento.guardar(clave, contenido);
        return clave;
    }

    public Optional<byte[]> leer(String clave) {
        if (!ArchivosPrivados.claveValida(clave)) {
            return Optional.empty();
        }
        return almacenamiento.leer(clave);
    }

    public void eliminar(String clave) {
        if (ArchivosPrivados.claveValida(clave)) {
            almacenamiento.eliminar(clave);
        }
    }
}
