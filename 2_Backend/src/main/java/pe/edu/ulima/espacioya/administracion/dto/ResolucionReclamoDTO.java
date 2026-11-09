package pe.edu.ulima.espacioya.administracion.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Decisión del admin sobre un reclamo (HU-26 R42–R53). {@code reembolso} y {@code montoReembolso} solo cuentan a favor
 * del usuario y los valida el servicio, que conoce el monto pagado; a favor del arrendador se ignoran (R51).
 */
public record ResolucionReclamoDTO(
        @NotNull(message = ResolucionReclamoDTO.M_A_FAVOR)
        @Pattern(regexp = "USUARIO|ARRENDADOR", message = ResolucionReclamoDTO.M_A_FAVOR)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String aFavorDe,

        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String reembolso,

        @JsonDeserialize(using = LectoresJson.SoloNumero.class)
        BigDecimal montoReembolso,

        @NotBlank(message = ResolucionReclamoDTO.M_RESOLUCION)
        @Size(max = 500, message = ResolucionReclamoDTO.M_RESOLUCION)
        @JsonDeserialize(using = LectoresJson.SoloTexto.class)
        String resolucion) {

    public static final String M_A_FAVOR = "Elige a favor de quién se resuelve.";
    public static final String M_RESOLUCION = "Escribe el motivo de la resolución en hasta 500 caracteres.";
}
