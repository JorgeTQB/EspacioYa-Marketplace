package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesafioDTO(
        @NotBlank(message = VerificacionMfaDTO.MENSAJE_DESAFIO)
        @Size(max = 64, message = VerificacionMfaDTO.MENSAJE_DESAFIO)
        String desafio) {

    @Override
    public String toString() {
        return "DesafioDTO[desafio=***]";
    }
}
