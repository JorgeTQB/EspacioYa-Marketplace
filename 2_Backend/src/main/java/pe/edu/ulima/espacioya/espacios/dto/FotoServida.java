package pe.edu.ulima.espacioya.espacios.dto;

/**
 * Foto lista para servir (HU-07 R39, R54): {@code publica} decide su Cache-Control. No sale como JSON.
 */
public record FotoServida(byte[] contenido, String tipoMime, boolean publica) {
}
