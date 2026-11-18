package pe.edu.ulima.espacioya.cuentas.dto;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;

/**
 * Perfil del titular de la sesión (HU-20 R5). Sin hash de contraseña, huella del documento, clave de la foto ni tokens
 * (R6). {@code facturacion} es {@code null} para un {@code USUARIO} (D7).
 */
public record PerfilDTO(String rol, String nombre, String telefono, String correo, String correoPendiente,
                        boolean tieneFoto, FacturacionDTO facturacion) {

    public static PerfilDTO de(Usuario usuario, PerfilArrendador perfil) {
        return new PerfilDTO(usuario.getRol().name(), usuario.getNombre(), usuario.getTelefono(), usuario.getCorreo(),
                usuario.getCorreoPendiente(), usuario.tieneFoto(), perfil == null ? null : FacturacionDTO.de(perfil));
    }
}
