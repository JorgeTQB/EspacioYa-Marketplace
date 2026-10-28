package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitudRecuperacionDTO(
        @NotBlank(message = MENSAJE_CORREO)
        @Email(message = MENSAJE_CORREO)
        @Size(max = 254, message = MENSAJE_CORREO)
        String correo) {

    public static final String MENSAJE_CORREO = RegistroUsuarioDTO.MENSAJE_CORREO;

    @Override
    public String toString() {
        return "SolicitudRecuperacionDTO[correo=***]";
    }
}
