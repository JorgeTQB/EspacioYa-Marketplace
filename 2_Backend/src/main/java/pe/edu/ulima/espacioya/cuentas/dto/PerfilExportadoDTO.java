package pe.edu.ulima.espacioya.cuentas.dto;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;

/**
 * Bloque {@code perfil} de la exportación (HU-22 R3, R4). Sin hash de contraseña, huella del documento, clave de la
 * foto ni secreto MFA (R13). {@code facturacion} es {@code null} para un {@code USUARIO}.
 */
public record PerfilExportadoDTO(String rol, String correo, String correoPendiente, String nombre, String telefono,
                                 boolean tieneFoto, boolean mfaActivo, ConsentimientoDTO consentimiento,
                                 FacturacionExportadaDTO facturacion) {

    public static PerfilExportadoDTO de(Usuario cuenta, PerfilArrendador perfil, boolean mfaActivo) {
        return new PerfilExportadoDTO(cuenta.getRol().name(), cuenta.getCorreo(), cuenta.getCorreoPendiente(),
                cuenta.getNombre(), cuenta.getTelefono(), cuenta.tieneFoto(), mfaActivo,
                new ConsentimientoDTO(cuenta.getVersionPolitica(), cuenta.getFechaConsentimiento()),
                perfil == null ? null : FacturacionExportadaDTO.de(perfil));
    }
}
