package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// max = 72 porque bcrypt solo usa los primeros 72 bytes (como InicioSesionDTO).
public record DesactivacionMfaDTO(
        @NotBlank(message = MENSAJE_CONTRASENA)
        @Size(max = 72, message = MENSAJE_CONTRASENA)
        String contrasena,

        @NotBlank(message = VerificacionMfaDTO.MENSAJE_CODIGO)
        @Size(max = 20, message = VerificacionMfaDTO.MENSAJE_CODIGO)
        String codigo) {

    public static final String MENSAJE_CONTRASENA = "Ingresa tu contraseña.";

    @Override
    public String toString() {
        return "DesactivacionMfaDTO[contrasena=***, codigo=***]";
    }
}
