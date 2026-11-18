package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil;

// max = 72 en la contraseña porque bcrypt solo usa los primeros 72 bytes (como DesactivacionMfaDTO).
public record SolicitudCambioCorreoDTO(
        @NotBlank(message = SolicitudCambioCorreoDTO.MENSAJE_CORREO)
        @Email(message = SolicitudCambioCorreoDTO.MENSAJE_CORREO)
        @Size(max = 254, message = SolicitudCambioCorreoDTO.MENSAJE_CORREO)
        String correo,

        @NotBlank(message = ReautenticacionPerfil.MENSAJE_CONTRASENA)
        @Size(max = 72, message = ReautenticacionPerfil.MENSAJE_CONTRASENA)
        String contrasena) {

    public static final String MENSAJE_CORREO = "Ingresa un correo válido.";

    @Override
    public String toString() {
        return "SolicitudCambioCorreoDTO[correo=***, contrasena=***]";
    }
}
