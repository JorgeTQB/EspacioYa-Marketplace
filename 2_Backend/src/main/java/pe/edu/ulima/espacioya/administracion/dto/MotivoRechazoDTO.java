package pe.edu.ulima.espacioya.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del rechazo de una identidad (HU-06, R35). El motivo se recorta antes de validarlo: solo espacios no es un
 * motivo y el límite de 500 caracteres cuenta el texto recortado.
 */
public record MotivoRechazoDTO(
        @NotBlank(message = MotivoRechazoDTO.M_MOTIVO)
        @Size(max = MotivoRechazoDTO.LONGITUD_MAXIMA, message = MotivoRechazoDTO.M_MOTIVO)
        String motivo) {

    public static final String M_MOTIVO = "Escribe el motivo del rechazo (hasta 500 caracteres).";
    public static final int LONGITUD_MAXIMA = 500;

    public MotivoRechazoDTO {
        motivo = motivo == null ? null : motivo.strip();
    }
}
