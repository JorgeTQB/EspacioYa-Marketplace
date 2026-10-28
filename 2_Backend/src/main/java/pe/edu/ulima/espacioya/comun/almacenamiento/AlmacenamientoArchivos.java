package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.util.Optional;

/**
 * Adapter del proveedor de almacenamiento privado (disco local hoy; S3 más adelante). Guarda bytes ya
 * cifrados por {@link ArchivosPrivados}: fuera de {@code comun.almacenamiento} nadie lo usa directamente.
 */
public interface AlmacenamientoArchivos {

    void guardar(String clave, byte[] contenido);

    Optional<byte[]> leer(String clave);

    void eliminar(String clave);
}
