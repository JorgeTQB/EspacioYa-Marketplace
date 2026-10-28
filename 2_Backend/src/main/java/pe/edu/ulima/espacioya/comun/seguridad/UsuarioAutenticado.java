package pe.edu.ulima.espacioya.comun.seguridad;

public record UsuarioAutenticado(Long usuarioId, Long sesionId, String rol) {
}
