package pe.edu.ulima.espacioya.cuentas.service;

import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;

/**
 * Aviso que acompaña a la sesión. Hoy solo el arrendador que no inició la verificación de identidad lo
 * recibe; HU-05 define los de EN_REVISION y RECHAZADA.
 */
final class AvisoCuenta {

    private AvisoCuenta() {
    }

    static String para(Usuario usuario, PerfilArrendadorRepository perfiles) {
        if (usuario.getRol() != Rol.ARRENDADOR) {
            return null;
        }
        return perfiles.findById(usuario.getId())
                .filter(perfil -> perfil.getEstadoIdentidad() == EstadoIdentidad.NO_INICIADA)
                .map(perfil -> PerfilArrendador.AVISO_IDENTIDAD)
                .orElse(null);
    }
}
