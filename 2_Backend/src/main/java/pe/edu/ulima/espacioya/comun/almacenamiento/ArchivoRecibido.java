package pe.edu.ulima.espacioya.comun.almacenamiento;

/**
 * Archivo tal como llegó en la solicitud. {@code contenido} es {@code null} si no se envió o si no se
 * leyó porque {@code tamano} ya superaba el límite.
 */
public record ArchivoRecibido(byte[] contenido, long tamano) {
}
