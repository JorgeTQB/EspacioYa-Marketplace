package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;

public record RegistroUsuarioDTO(
        @NotBlank(message = MENSAJE_CORREO)
        @Email(message = MENSAJE_CORREO)
        @Size(max = 254, message = MENSAJE_CORREO)
        String correo,

        @NotNull(message = PoliticaContrasena.REQUISITOS)
        String contrasena,

        @NotNull(message = MENSAJE_ACEPTACION)
        @AssertTrue(message = MENSAJE_ACEPTACION)
        Boolean aceptaPrivacidad,

        @NotBlank(message = MENSAJE_VERSION_DESACTUALIZADA)
        String versionPolitica) {

    public static final String MENSAJE_CORREO = "Ingresa un correo válido.";
    public static final String MENSAJE_VERSION_DESACTUALIZADA =
            "La política de privacidad se actualizó. Recarga la página y vuelve a aceptarla.";
    public static final String MENSAJE_ACEPTACION = "Debes aceptar los términos y la política de privacidad.";

    @Override
    public String toString() {
        return "RegistroUsuarioDTO[correo=***, contrasena=***, aceptaPrivacidad=" + aceptaPrivacidad
                + ", versionPolitica=" + versionPolitica + "]";
    }
}
