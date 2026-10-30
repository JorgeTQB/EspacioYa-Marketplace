package pe.edu.ulima.espacioya.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la suspensión de una cuenta (HU-09, R19). El motivo se recorta antes de validarlo: solo espacios no es un
 * motivo y el límite de 500 caracteres cuenta el texto recortado.
 */
public record MotivoSuspensionDTO(
        @NotBlank(message = MotivoSuspensionDTO.M_MOTIVO)
        @Size(max = MotivoSuspensionDTO.LONGITUD_MAXIMA, message = MotivoSuspensionDTO.M_MOTIVO)
        String motivo) {

    public static final String M_MOTIVO = "Escribe el motivo de la suspensión (hasta 500 caracteres).";
    public static final int LONGITUD_MAXIMA = 500;

    public MotivoSuspensionDTO {
        motivo = motivo == null ? null : motivo.strip();
    }
}
