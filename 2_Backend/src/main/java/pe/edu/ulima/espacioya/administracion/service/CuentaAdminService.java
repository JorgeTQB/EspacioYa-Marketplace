package pe.edu.ulima.espacioya.administracion.service;

import static pe.edu.ulima.espacioya.comun.seguridad.RolesSesion.exigirAdmin;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.domain.AccionSuspension;
import pe.edu.ulima.espacioya.administracion.domain.SuspensionCuenta;
import pe.edu.ulima.espacioya.administracion.dto.AccionCuentaDTO;
import pe.edu.ulima.espacioya.administracion.dto.CuentaAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.SuspensionVigenteDTO;
import pe.edu.ulima.espacioya.administracion.repository.SuspensionCuentaRepository;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.BusquedaCuentasDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;

/**
 * Suspensión y reactivación de cuentas (HU-09). {@code administracion} es dueño del caso de uso y del historial
 * ({@code suspension_cuenta}); {@code cuentas} es dueño del estado y de las sesiones y los cambia vía
 * {@link CuentaService}. Cada acción cambia el estado, guarda el registro y lo audita en una sola transacción: si algo
 * falla no queda nada, ni el correo.
 */
@Service
public class CuentaAdminService {

    public static final String ACCION_SUSPENDIDA = "CUENTA_SUSPENDIDA";
    public static final String ACCION_REACTIVADA = "CUENTA_REACTIVADA";
    public static final String ENTIDAD_USUARIO = "usuario";
    public static final String CAMPO_BUSQUEDA = "q";
    public static final String M_BUSQUEDA = "Escribe de 3 a 100 caracteres del correo o del nombre.";
    public static final int LONGITUD_MINIMA_BUSQUEDA = 3;
    public static final int LONGITUD_MAXIMA_BUSQUEDA = 100;

    private static final String ESTADO_SUSPENDIDA = "SUSPENDIDA";

    private final CuentaService cuentas;
    private final SuspensionCuentaRepository suspensiones;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public CuentaAdminService(CuentaService cuentas, SuspensionCuentaRepository suspensiones,
                              AuditoriaService auditoria, Clock reloj) {
        this.cuentas = cuentas;
        this.suspensiones = suspensiones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public BusquedaCuentasDTO buscar(UsuarioAutenticado admin, String q) {
        exigirAdmin(admin);
        var texto = q == null ? null : q.strip();
        if (texto == null || texto.length() < LONGITUD_MINIMA_BUSQUEDA || texto.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            throw new ReglaNegocioException(CAMPO_BUSQUEDA, M_BUSQUEDA);
        }
        return cuentas.buscarCuentas(admin, texto);
    }

    @Transactional(readOnly = true)
    public CuentaAdminDTO detalle(UsuarioAutenticado admin, Long id) {
        exigirAdmin(admin);
        var cuenta = cuentas.detalleCuenta(admin, id);
        SuspensionVigenteDTO suspension = null;
        if (ESTADO_SUSPENDIDA.equals(cuenta.estado())) {
            suspension = suspensiones.findFirstByUsuarioIdAndAccionOrderByFechaDescIdDesc(id,
                            AccionSuspension.SUSPENSION)
                    .map(registro -> new SuspensionVigenteDTO(registro.getMotivo(), registro.getFecha()))
                    .orElse(null);
        }
        return new CuentaAdminDTO(cuenta, suspension);
    }

    @Transactional
    public AccionCuentaDTO suspender(UsuarioAutenticado admin, Long id, String motivo, String ip) {
        exigirAdmin(admin);
        var cambio = cuentas.suspenderCuenta(admin, id, motivo);
        var ahora = Instant.now(reloj);
        var registro = suspensiones.save(SuspensionCuenta.suspender(id, admin.usuarioId(), motivo, ahora));
        auditar(ACCION_SUSPENDIDA, admin, registro, ip, ";sesionesRevocadas=" + cambio.sesionesRevocadas());
        return new AccionCuentaDTO(registro.getAccion().name(), cambio.estado(), ahora);
    }

    @Transactional
    public AccionCuentaDTO reactivar(UsuarioAutenticado admin, Long id, String ip) {
        exigirAdmin(admin);
        var cambio = cuentas.reactivarCuenta(admin, id);
        var ahora = Instant.now(reloj);
        var registro = suspensiones.save(SuspensionCuenta.reactivar(id, admin.usuarioId(), ahora));
        auditar(ACCION_REACTIVADA, admin, registro, ip, "");
        return new AccionCuentaDTO(registro.getAccion().name(), cambio.estado(), ahora);
    }

    // El motivo vive solo en suspension_cuenta y el correo solo en cuentas: la auditoría no los duplica (R46).
    private void auditar(String accion, UsuarioAutenticado admin, SuspensionCuenta registro, String ip, String extra) {
        var detalle = "accion=" + registro.getAccion() + ";registro=" + registro.getId() + extra;
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), accion, ENTIDAD_USUARIO, registro.getUsuarioId(),
                ip, detalle));
    }
}
