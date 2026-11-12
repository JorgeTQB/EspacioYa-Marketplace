package pe.edu.ulima.espacioya.administracion.service;

import static pe.edu.ulima.espacioya.comun.seguridad.RolesSesion.exigirAdmin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.domain.RevisionIdentidad;
import pe.edu.ulima.espacioya.administracion.dto.RevisionIdentidadDTO;
import pe.edu.ulima.espacioya.administracion.repository.RevisionIdentidadRepository;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ExpedienteIdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;

/**
 * Revisión de identidad de arrendadores (HU-06). {@code administracion} es dueño del caso de uso y del historial
 * de decisiones; {@code cuentas} es dueño del estado y lo cambia vía {@link CuentaService}. Cada decisión cambia el
 * estado, guarda la revisión y la audita en una sola transacción: si algo falla no queda nada, ni el correo.
 */
@Service
public class RevisionIdentidadService {

    public static final String ACCION_APROBADA = "IDENTIDAD_APROBADA";
    public static final String ACCION_RECHAZADA = "IDENTIDAD_RECHAZADA";
    public static final String ENTIDAD_PERFIL = "perfil_arrendador";

    private final CuentaService cuentas;
    private final RevisionIdentidadRepository revisiones;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public RevisionIdentidadService(CuentaService cuentas, RevisionIdentidadRepository revisiones,
                                    AuditoriaService auditoria, Clock reloj) {
        this.cuentas = cuentas;
        this.revisiones = revisiones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<ArrendadorEnRevisionDTO> cola(UsuarioAutenticado admin) {
        exigirAdmin(admin);
        return cuentas.arrendadoresEnRevision(admin);
    }

    @Transactional(readOnly = true)
    public ExpedienteIdentidadDTO expediente(UsuarioAutenticado admin, Long id) {
        exigirAdmin(admin);
        return cuentas.expedienteIdentidad(admin, id);
    }

    @Transactional
    public RevisionIdentidadDTO aprobar(UsuarioAutenticado admin, Long id, String ip) {
        exigirAdmin(admin);
        var estado = cuentas.aprobarIdentidad(admin, id);
        var ahora = Instant.now(reloj);
        var revision = revisiones.save(RevisionIdentidad.aprobar(id, admin.usuarioId(), ahora));
        auditar(ACCION_APROBADA, admin, revision, ip);
        return new RevisionIdentidadDTO(revision.getDecision().name(), estado, ahora);
    }

    @Transactional
    public RevisionIdentidadDTO rechazar(UsuarioAutenticado admin, Long id, String motivo, String ip) {
        exigirAdmin(admin);
        var estado = cuentas.rechazarIdentidad(admin, id, motivo);
        var ahora = Instant.now(reloj);
        var revision = revisiones.save(RevisionIdentidad.rechazar(id, admin.usuarioId(), motivo, ahora));
        auditar(ACCION_RECHAZADA, admin, revision, ip);
        return new RevisionIdentidadDTO(revision.getDecision().name(), estado, ahora);
    }

    // El motivo vive solo en revision_identidad y en el perfil: la auditoría no lo duplica (S16, R43).
    private void auditar(String accion, UsuarioAutenticado admin, RevisionIdentidad revision, String ip) {
        var detalle = "decision=" + revision.getDecision() + ";revision=" + revision.getId();
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), accion, ENTIDAD_PERFIL, revision.getArrendadorId(),
                ip, detalle));
    }
}
