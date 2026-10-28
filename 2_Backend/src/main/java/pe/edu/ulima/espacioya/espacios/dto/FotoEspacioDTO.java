package pe.edu.ulima.espacioya.espacios.dto;

/**
 * Foto de un espacio con su URL estable {@code /api/fotos/<UUID>} (HU-07 R11), sin firma ni vencimiento.
 */
public record FotoEspacioDTO(Long id, int orden, String url) {
}
