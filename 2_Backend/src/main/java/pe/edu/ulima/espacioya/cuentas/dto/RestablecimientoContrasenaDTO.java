package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotNull;

import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;

public record RestablecimientoContrasenaDTO(
        String token,

        @NotNull(message = PoliticaContrasena.REQUISITOS)
        String contrasena) {

    @Override
    public String toString() {
        return "RestablecimientoContrasenaDTO[token=***, contrasena=***]";
    }
}
