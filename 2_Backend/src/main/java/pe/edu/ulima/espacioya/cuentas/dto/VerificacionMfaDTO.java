package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificacionMfaDTO(
        @NotBlank(message = MENSAJE_DESAFIO)
        @Size(max = 64, message = MENSAJE_DESAFIO)
        String desafio,

        @NotBlank(message = MENSAJE_CODIGO)
        @Size(max = 20, message = MENSAJE_CODIGO)
        String codigo) {

    public static final String MENSAJE_DESAFIO = "Inicia sesión de nuevo.";
    public static final String MENSAJE_CODIGO = "Ingresa el código de verificación.";

    @Override
    public String toString() {
        return "VerificacionMfaDTO[desafio=***, codigo=***]";
    }
}
