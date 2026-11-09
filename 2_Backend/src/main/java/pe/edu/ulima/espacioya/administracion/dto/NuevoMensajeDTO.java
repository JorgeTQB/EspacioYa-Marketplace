package pe.edu.ulima.espacioya.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.annotation.JsonDeserialize;

/** Mensaje de una parte en el hilo de un reclamo (HU-26 R23). El autor y su papel salen de la sesión. */
public record NuevoMensajeDTO(
        @NotBlank(message = NuevoMensajeDTO.M_TEXTO)
        @Size(max = 1000, message = NuevoMensajeDTO.M_TEXTO)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String texto) {

    public static final String M_TEXTO = "Escribe un mensaje de hasta 1000 caracteres.";
}
