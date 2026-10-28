package pe.edu.ulima.espacioya.comun.seguridad;

import java.util.Optional;

/**
 * Puerto que usa el filtro de seguridad para validar el token de sesión. Lo implementa el módulo
 * {@code cuentas}; así {@code comun} no depende de él.
 */
public interface ValidadorSesion {

    /**
     * Devuelve el usuario de la sesión y la renueva si el token corresponde a una sesión vigente;
     * vacío si no existe, está revocada o venció.
     */
    Optional<UsuarioAutenticado> validar(String token);
}
