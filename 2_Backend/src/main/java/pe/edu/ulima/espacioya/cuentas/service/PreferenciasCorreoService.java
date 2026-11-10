package pe.edu.ulima.espacioya.cuentas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.PreferenciasCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * Preferencia de recordatorios del titular de la sesión (HU-25, CA2, RF-A-26). Solo apaga el recordatorio de
 * reserva: los correos de pago y cancelación no la consultan (D7, RN-14). El admin recibe 403 sin leer nada (R5).
 */
@Service
public class PreferenciasCorreoService {

    public static final String ACCION_PREFERENCIA = "PREFERENCIA_RECORDATORIOS";

    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;

    public PreferenciasCorreoService(UsuarioRepository usuarios, AuditoriaService auditoria,
                                     TransactionTemplate transacciones) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
    }

    @Transactional(readOnly = true)
    public PreferenciasCorreoDTO consultar(UsuarioAutenticado usuario) {
        PerfilService.exigirTitular(usuario);
        var cuenta = usuarios.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
        return new PreferenciasCorreoDTO(cuenta.tieneRecordatoriosActivos());
    }

    public PreferenciasCorreoDTO actualizar(UsuarioAutenticado usuario, PreferenciasCorreoDTO datos, String ip) {
        PerfilService.exigirTitular(usuario);
        var id = usuario.usuarioId();
        // Nunca nulo: @NotNull en el DTO responde 400 antes de llegar aquí (R4).
        boolean nuevo = datos.recordatoriosActivos();
        return transacciones.execute(estado -> {
            var cuenta = usuarios.findParaActualizar(id)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
            var anterior = cuenta.tieneRecordatoriosActivos();
            if (cuenta.cambiarRecordatorios(nuevo)) {
                auditoria.registrar(new EventoAuditoria(id, ACCION_PREFERENCIA, PerfilService.ENTIDAD_USUARIO, id, ip,
                        "recordatoriosActivos=" + anterior + "->" + nuevo));
            }
            return new PreferenciasCorreoDTO(cuenta.tieneRecordatoriosActivos());
        });
    }
}
