package pe.edu.ulima.espacioya.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Decisión del admin sobre un reporte (HU-27 R31–R44). {@code OCULTAR_POR_FRAUDE} solo vale para un espacio: lo
 * comprueba el servicio, que conoce el tipo del reporte (R63). El motivo se sanitiza en el servicio.
 */
public record DecisionReporteDTO(
        @NotNull(message = DecisionReporteDTO.M_DECISION)
        @Pattern(regexp = "MANTENER|OCULTAR|OCULTAR_POR_FRAUDE", message = DecisionReporteDTO.M_DECISION)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String decision,

        @NotBlank(message = DecisionReporteDTO.M_MOTIVO_DECISION)
        @Size(max = 500, message = DecisionReporteDTO.M_MOTIVO_DECISION)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String motivoDecision) {

    public static final String M_DECISION = "Elige mantener u ocultar el contenido.";
    public static final String M_MOTIVO_DECISION = "Escribe el motivo de la decisión en hasta 500 caracteres.";
}
