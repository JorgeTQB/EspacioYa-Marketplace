package pe.edu.ulima.espacioya.cuentas.service;

import java.util.List;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ExpedienteIdentidadDTO;
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

    // Revisión de identidad (HU-06). Cada método lanza AccesoDenegadoException si el rol de la sesión no es ADMIN.

    /** Arrendadores {@code ACTIVA} con la identidad {@code EN_REVISION}, del envío más antiguo al más reciente. */
    List<ArrendadorEnRevisionDTO> arrendadoresEnRevision(UsuarioAutenticado admin);

    /**
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si el arrendador no está en la cola.
     */
    ExpedienteIdentidadDTO expedienteIdentidad(UsuarioAutenticado admin, Long arrendadorId);

    /**
     * Deja la identidad {@code VERIFICADA} y avisa al arrendador por correo después del commit.
     *
     * @return el estado de identidad resultante.
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si no es un arrendador activo.
     * @throws pe.edu.ulima.espacioya.comun.error.ReglaNegocioException si la identidad ya no está en revisión.
     */
    String aprobarIdentidad(UsuarioAutenticado admin, Long arrendadorId);

    /**
     * Deja la identidad {@code RECHAZADA} con el motivo y avisa al arrendador por correo después del commit.
     *
     * @return el estado de identidad resultante.
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si no es un arrendador activo.
     * @throws pe.edu.ulima.espacioya.comun.error.ReglaNegocioException si la identidad ya no está en revisión.
     */
    String rechazarIdentidad(UsuarioAutenticado admin, Long arrendadorId, String motivo);
}
