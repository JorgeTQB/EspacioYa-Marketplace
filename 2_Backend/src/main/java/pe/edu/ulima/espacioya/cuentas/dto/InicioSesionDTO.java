package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sin @Email ni política de contraseña: cualquier valor que no coincida es el mismo 401 genérico.
// max = 72 porque bcrypt solo usa los primeros 72 bytes.
public record InicioSesionDTO(
        @NotBlank(message = MENSAJE_CORREO)
        @Size(max = 254, message = MENSAJE_CORREO)
        String correo,

        @NotBlank(message = MENSAJE_CONTRASENA)
        @Size(max = 72, message = MENSAJE_CONTRASENA)
        String contrasena) {

    public static final String MENSAJE_CORREO = "Ingresa tu correo.";
    public static final String MENSAJE_CONTRASENA = "Ingresa tu contraseña.";

    @Override
    public String toString() {
        return "InicioSesionDTO[correo=***, contrasena=***]";
    }
}
