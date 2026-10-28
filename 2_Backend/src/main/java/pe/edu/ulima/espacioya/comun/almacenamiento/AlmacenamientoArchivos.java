package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.util.Optional;

/**
 * Adapter del proveedor de almacenamiento (disco local hoy; S3 más adelante). Lo usan {@link ArchivosPrivados}
 * (documentos, cifrados) y {@link ArchivosPublicos} (fotos de espacios, sin cifrar): fuera de
 * {@code comun.almacenamiento} nadie lo usa directamente.
 */
public interface AlmacenamientoArchivos {

    void guardar(String clave, byte[] contenido);

    Optional<byte[]> leer(String clave);

    void eliminar(String clave);
}
