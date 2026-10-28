package pe.edu.ulima.espacioya.cuentas.service;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;

/**
 * Interfaz pública de {@code cuentas} para otros módulos (docs/architecture.md, principio 5): ellos nunca
 * usan los repositorios de {@code cuentas}.
 */
public interface CuentaService {

    /**
     * @throws pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException si el rol de la sesión no es
     *         {@code ARRENDADOR}.
     */
    HabilitacionArrendadorDTO habilitacionArrendador(UsuarioAutenticado usuario);
}
