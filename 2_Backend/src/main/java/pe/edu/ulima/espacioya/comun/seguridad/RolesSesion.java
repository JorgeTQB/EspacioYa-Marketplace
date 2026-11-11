package pe.edu.ulima.espacioya.comun.seguridad;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;

/**
 * Comprobación del rol de la sesión en los servicios del admin (HU-27 DT-13, RNF-SEG-18). Compara con el texto
 * {@code "ADMIN"}: {@code comun} no depende del enum de roles de {@code cuentas}.
 */
public final class RolesSesion {

    public static final String ROL_ADMIN = "ADMIN";

    private RolesSesion() {
    }

    /** Sin sesión o con un rol distinto de ADMIN → {@link AccesoDenegadoException} (403), antes de leer nada. */
    public static void exigirAdmin(UsuarioAutenticado sesion) {
        if (sesion == null || !ROL_ADMIN.equals(sesion.rol())) {
            throw new AccesoDenegadoException();
        }
    }
}
