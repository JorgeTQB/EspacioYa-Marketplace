package pe.edu.ulima.espacioya.cuentas.dto;

/**
 * Datos de una cuenta para el admin (HU-09, R7–R9). Nunca documentos, número de documento, razón social ni
 * dirección fiscal (minimización). Las banderas las decide el dominio para que la pantalla no aplique reglas.
 */
public record DetalleCuentaDTO(Long id, String nombre, String correo, String telefono, String rol, String estado,
                               boolean correoVerificado, String estadoIdentidad, boolean puedeSuspender,
                               boolean puedeReactivar) {
}
