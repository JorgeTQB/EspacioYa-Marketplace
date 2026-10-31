package pe.edu.ulima.espacioya.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** El único dato que el cliente elige: el monto sale de la reserva y los campos extra se ignoran (HU-13 R71). */
public record SimularPagoDTO(
        @NotNull(message = SimularPagoDTO.M_RESULTADO)
        @Pattern(regexp = "APROBADO|RECHAZADO", message = SimularPagoDTO.M_RESULTADO)
        String resultado) {

    public static final String M_RESULTADO = "Elige un resultado válido.";
}
