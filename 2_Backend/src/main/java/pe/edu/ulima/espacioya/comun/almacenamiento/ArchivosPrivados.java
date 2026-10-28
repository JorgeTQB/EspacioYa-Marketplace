package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import pe.edu.ulima.espacioya.comun.cifrado.CifradorArchivos;

/**
 * Almacenamiento privado cifrado con URL firmada (componente transversal, HU-05). Siempre cifra antes de
 * delegar en el adaptador y valida la forma de la clave antes de tocarlo (sin path traversal).
 */
@Service
public class ArchivosPrivados {

    private static final Pattern CARPETA = Pattern.compile("^[a-z]+$");
    private static final Pattern CLAVE = Pattern.compile(
            "^[a-z]+/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final AlmacenamientoArchivos almacenamiento;
    private final CifradorArchivos cifrador;
    private final FirmadorUrl firmador;

    public ArchivosPrivados(AlmacenamientoArchivos almacenamiento, CifradorArchivos cifrador, FirmadorUrl firmador) {
        this.almacenamiento = almacenamiento;
        this.cifrador = cifrador;
        this.firmador = firmador;
    }

    public String guardar(String carpeta, byte[] contenido) {
        if (carpeta == null || !CARPETA.matcher(carpeta).matches()) {
            throw new IllegalArgumentException("Carpeta de almacenamiento no válida");
        }
        var clave = carpeta + "/" + UUID.randomUUID();
        almacenamiento.guardar(clave, cifrador.cifrar(contenido));
        return clave;
    }

    public Optional<byte[]> leer(String clave) {
        if (!claveValida(clave)) {
            return Optional.empty();
        }
        return almacenamiento.leer(clave).map(cifrador::descifrar);
    }

    public void eliminar(String clave) {
        if (claveValida(clave)) {
            almacenamiento.eliminar(clave);
        }
    }

    public UrlFirmada urlFirmada(String clave, String tipoMime) {
        return firmador.firmar(clave, tipoMime);
    }

    static boolean claveValida(String clave) {
        return clave != null && CLAVE.matcher(clave).matches();
    }
}
