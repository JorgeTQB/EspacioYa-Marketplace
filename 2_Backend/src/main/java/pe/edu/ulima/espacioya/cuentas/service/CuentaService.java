package pe.edu.ulima.espacioya.cuentas.service;

import java.util.List;
import java.util.Set;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.BusquedaCuentasDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CambioEstadoCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DetalleCuentaDTO;
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

    // Suspensión y reactivación de cuentas (HU-09). Los cuatro primeros lanzan AccesoDenegadoException si el rol de
    // la sesión no es ADMIN, sin consultar los repositorios (R40).

    /**
     * Cuentas cuyo correo o nombre contiene el texto, sin distinguir mayúsculas y tratando {@code %}, {@code _} y
     * {@code \} como literales; como mucho 20, por correo ascendente, y {@code hayMas} si hay más.
     *
     * @param textoRecortado texto ya recortado y validado (de 3 a 100 caracteres) por el llamador.
     */
    BusquedaCuentasDTO buscarCuentas(UsuarioAutenticado admin, String textoRecortado);

    /**
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si la cuenta no existe.
     */
    DetalleCuentaDTO detalleCuenta(UsuarioAutenticado admin, Long cuentaId);

    /**
     * Bloquea la cuenta, la deja {@code SUSPENDIDA}, revoca todas sus sesiones y avisa por correo con el motivo
     * después del commit. Se une a la transacción del llamador.
     *
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si la cuenta no existe.
     * @throws pe.edu.ulima.espacioya.comun.error.ReglaNegocioException si no es gestionable o ya está suspendida.
     */
    CambioEstadoCuentaDTO suspenderCuenta(UsuarioAutenticado admin, Long cuentaId, String motivo);

    /**
     * Bloquea la cuenta, la deja {@code ACTIVA} y avisa por correo después del commit. Se une a la transacción del
     * llamador.
     *
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si la cuenta no existe.
     * @throws pe.edu.ulima.espacioya.comun.error.ReglaNegocioException si no es gestionable o ya está activa.
     */
    CambioEstadoCuentaDTO reactivarCuenta(UsuarioAutenticado admin, Long cuentaId);

    /**
     * {@code true} solo si la cuenta existe y está {@code ACTIVA}. No exige sesión ni rol: lo usan rutas públicas
     * para ocultar lo de una cuenta suspendida (HU-09 D1; p. ej. las fotos y las horas libres de sus espacios).
     */
    boolean cuentaActiva(Long usuarioId);

    /**
     * Ids de los arrendadores con estado distinto de {@code ACTIVA}, o un conjunto vacío. No exige sesión ni rol
     * (HU-09 D1): {@code busqueda} lo usa para excluir sus espacios ({@code e.arrendadorId not in :ids}). Si el
     * conjunto está vacío, el llamador omite esa condición: una colección vacía en {@code not in} no es portable.
     */
    Set<Long> arrendadoresNoActivos();
}
