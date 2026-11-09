package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un reclamo en la exportación de datos personales (HU-22, HU-26 R67, R68): solo los textos que escribió el titular,
 * nunca los de la otra parte ni la resolución del admin.
 */
public record ReclamoExportadoDTO(Long reservaId, String espacioNombre, String motivo, String estado,
                                  Instant abiertoEn, Instant resueltoEn, String aFavorDe, List<String> misTextos) {
}
