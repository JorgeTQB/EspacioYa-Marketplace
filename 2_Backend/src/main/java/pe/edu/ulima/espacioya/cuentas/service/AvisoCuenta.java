package pe.edu.ulima.espacioya.cuentas.service;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;

/**
 * Aviso que acompaña a la sesión. Solo lo recibe el arrendador, según el estado de su identidad
 * ({@link PerfilArrendador#avisoIdentidad()}: HU-02 para NO_INICIADA, HU-05 para EN_REVISION y RECHAZADA).
 */
final class AvisoCuenta {

    private AvisoCuenta() {
    }

    static String para(Usuario usuario, PerfilArrendadorRepository perfiles) {
        if (usuario.getRol() != Rol.ARRENDADOR) {
            return null;
        }
        return perfiles.findById(usuario.getId())
                .map(PerfilArrendador::avisoIdentidad)
                .orElse(null);
    }
}
