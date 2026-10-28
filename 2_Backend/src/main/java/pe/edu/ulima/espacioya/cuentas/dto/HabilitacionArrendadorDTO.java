package pe.edu.ulima.espacioya.cuentas.dto;

/**
 * Qué puede hacer un arrendador con sus espacios (RN-01): crear borradores con el correo verificado y
 * publicarlos con la identidad verificada. Lo consume {@code espacios} vía {@code CuentaService}.
 */
public record HabilitacionArrendadorDTO(boolean correoVerificado, boolean identidadVerificada) {
}
