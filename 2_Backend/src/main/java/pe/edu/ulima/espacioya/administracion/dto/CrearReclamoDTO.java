package pe.edu.ulima.espacioya.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Solicitud para abrir un reclamo (HU-26). El usuario sale de la sesión y el arrendador del espacio de la reserva:
 * cualquier otro campo se descarta (R3). Sin fotos: solo texto.
 */
public record CrearReclamoDTO(
        @NotNull(message = CrearReclamoDTO.M_RESERVA)
        @Positive(message = CrearReclamoDTO.M_RESERVA)
        @JsonDeserialize(using = LectoresJson.SoloEnteroLargo.class)
        Long reservaId,

        @NotNull(message = CrearReclamoDTO.M_MOTIVO)
        @Pattern(regexp = "ESPACIO_CERRADO|DISTINTO_A_LO_PUBLICADO|OTRO", message = CrearReclamoDTO.M_MOTIVO)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String motivo,

        @NotBlank(message = CrearReclamoDTO.M_DESCRIPCION)
        @Size(max = 1000, message = CrearReclamoDTO.M_DESCRIPCION)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String descripcion) {

    public static final String M_RESERVA = "Elige una reserva válida.";
    public static final String M_MOTIVO = "Elige un motivo de la lista.";
    public static final String M_DESCRIPCION = "Describe el problema en hasta 1000 caracteres.";
}
