package pe.edu.ulima.espacioya.administracion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Solicitud para reportar un espacio o una reseña (HU-27 CA1). El reportante sale de la sesión: cualquier otro campo
 * se descarta (R4). Motivo de catálogo, sin texto libre (P3).
 */
public record CrearReporteDTO(
        @NotNull(message = CrearReporteDTO.M_TIPO)
        @Pattern(regexp = "ESPACIO|RESENA", message = CrearReporteDTO.M_TIPO)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String tipoObjetivo,

        @NotNull(message = CrearReporteDTO.M_OBJETIVO)
        @Positive(message = CrearReporteDTO.M_OBJETIVO)
        @JsonDeserialize(using = LectoresJson.SoloEnteroLargo.class)
        Long objetivoId,

        @NotNull(message = CrearReporteDTO.M_MOTIVO)
        @Pattern(regexp = "FRAUDE|INFORMACION_FALSA|CONTENIDO_INAPROPIADO|SPAM", message = CrearReporteDTO.M_MOTIVO)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String motivo) {

    public static final String M_TIPO = "Elige si reportas un espacio o una reseña.";
    public static final String M_OBJETIVO = "Elige un contenido válido para reportar.";
    public static final String M_MOTIVO = "Elige un motivo de la lista.";
}
