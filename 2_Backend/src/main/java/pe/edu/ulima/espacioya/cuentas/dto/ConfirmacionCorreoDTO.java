package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import pe.edu.ulima.espacioya.cuentas.service.CambioCorreoService;

// El token viaja solo en el cuerpo (HU-20 R34); su forma exacta la revisa el servicio con TokenAleatorio.
public record ConfirmacionCorreoDTO(
        @NotBlank(message = CambioCorreoService.MENSAJE_ENLACE_INVALIDO)
        @Size(max = 100, message = CambioCorreoService.MENSAJE_ENLACE_INVALIDO)
        String token) {

    @Override
    public String toString() {
        return "ConfirmacionCorreoDTO[token=***]";
    }
}
