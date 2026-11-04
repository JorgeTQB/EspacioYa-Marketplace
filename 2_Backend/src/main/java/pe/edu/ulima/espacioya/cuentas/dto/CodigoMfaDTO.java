package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodigoMfaDTO(
        @NotBlank(message = VerificacionMfaDTO.MENSAJE_CODIGO)
        @Size(max = 20, message = VerificacionMfaDTO.MENSAJE_CODIGO)
        String codigo) {

    @Override
    public String toString() {
        return "CodigoMfaDTO[codigo=***]";
    }
}
