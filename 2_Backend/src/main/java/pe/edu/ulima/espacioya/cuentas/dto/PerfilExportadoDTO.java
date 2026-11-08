package pe.edu.ulima.espacioya.cuentas.dto;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;

/**
 * Bloque {@code perfil} de la exportación (HU-22 R3, R4). Sin hash de contraseña, huella del documento, clave de la
 * foto ni secreto MFA (R13). {@code facturacion} es {@code null} para un {@code USUARIO}.
 * {@code recordatoriosActivos} es la preferencia de recordatorios de la cuenta (HU-25 R37, D13), leída de la misma
 * entidad.
 */
public record PerfilExportadoDTO(String rol, String correo, String correoPendiente, String nombre, String telefono,
                                 boolean tieneFoto, boolean mfaActivo, boolean recordatoriosActivos,
                                 ConsentimientoDTO consentimiento, FacturacionExportadaDTO facturacion) {

    public static PerfilExportadoDTO de(Usuario cuenta, PerfilArrendador perfil, boolean mfaActivo) {
        return new PerfilExportadoDTO(cuenta.getRol().name(), cuenta.getCorreo(), cuenta.getCorreoPendiente(),
                cuenta.getNombre(), cuenta.getTelefono(), cuenta.tieneFoto(), mfaActivo,
                cuenta.tieneRecordatoriosActivos(),
                new ConsentimientoDTO(cuenta.getVersionPolitica(), cuenta.getFechaConsentimiento()),
                perfil == null ? null : FacturacionExportadaDTO.de(perfil));
    }
}
