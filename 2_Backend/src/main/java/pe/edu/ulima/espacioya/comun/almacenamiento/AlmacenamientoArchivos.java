package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.util.Optional;

/**
 * Target del Adapter de almacenamiento (HU-05, HU-29 ★). Dos adaptadores, elegidos con
 * {@code espacioya.almacenamiento.tipo} ({@code ALMACENAMIENTO_TIPO}): {@link AlmacenamientoLocal} (disco, por defecto)
 * y {@link AlmacenamientoS3} (bucket S3 privado). Lo usan {@link ArchivosPrivados} (documentos, cifrados) y
 * {@link ArchivosPublicos} (fotos de espacios, sin cifrar): fuera de {@code comun.almacenamiento} nadie lo usa
 * directamente.
 *
 * <p>Contrato común a los dos adaptadores: guardar de nuevo una clave reemplaza su contenido; {@code leer} devuelve
 * vacío si la clave no existe; {@code eliminar} no falla si la clave no existe; cualquier falla del proveedor es una
 * {@link java.io.UncheckedIOException} con el mismo mensaje en ambos; una clave fuera del espacio de claves (en blanco,
 * absoluta o con {@code ..}) es una {@link IllegalArgumentException}.
 */
public interface AlmacenamientoArchivos {

    void guardar(String clave, byte[] contenido);

    Optional<byte[]> leer(String clave);

    void eliminar(String clave);
}
