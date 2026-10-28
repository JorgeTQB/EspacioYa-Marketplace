package pe.edu.ulima.espacioya.comun.almacenamiento;

/**
 * Archivo aceptado: {@code contenido} es el que se almacena (las imágenes ya vueltas a codificar) y
 * {@code tamanoRecibido} los bytes que envió el usuario.
 */
public record ArchivoValidado(byte[] contenido, TipoArchivo tipo, long tamanoRecibido) {
}
