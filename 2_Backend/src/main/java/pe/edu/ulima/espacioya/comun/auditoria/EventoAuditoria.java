package pe.edu.ulima.espacioya.comun.auditoria;

public record EventoAuditoria(Long usuarioId, String accion, String entidad, Long entidadId,
                              String ip, String detalle) {
}
