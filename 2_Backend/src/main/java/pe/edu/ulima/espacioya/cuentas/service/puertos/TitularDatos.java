package pe.edu.ulima.espacioya.cuentas.service.puertos;

/** Cuenta de la sesión cuyos datos se exportan o se suprimen (HU-22). El rol es el nombre de {@code Rol}. */
public record TitularDatos(Long usuarioId, String rol) {

    public static final String ROL_USUARIO = "USUARIO";
    public static final String ROL_ARRENDADOR = "ARRENDADOR";

    public boolean esUsuario() {
        return ROL_USUARIO.equals(rol);
    }

    public boolean esArrendador() {
        return ROL_ARRENDADOR.equals(rol);
    }
}
