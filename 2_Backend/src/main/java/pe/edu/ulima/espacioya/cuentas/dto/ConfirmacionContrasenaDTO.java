package pe.edu.ulima.espacioya.cuentas.dto;

/**
 * Cuerpo de la exportación y de la eliminación de la cuenta (HU-22). Sin Bean Validation: la contraseña vacía la
 * rechaza {@code ReautenticacionPerfil} después del rol y del cupo (R15, R18, R19).
 */
public record ConfirmacionContrasenaDTO(String contrasena) {

    @Override
    public String toString() {
        return "ConfirmacionContrasenaDTO[contrasena=***]";
    }
}
