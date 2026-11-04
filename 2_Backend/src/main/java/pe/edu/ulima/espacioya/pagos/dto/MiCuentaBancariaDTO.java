package pe.edu.ulima.espacioya.pagos.dto;

/** Respuesta de {@code GET}: si la identidad está verificada y la cuenta vigente, o {@code null} si no hay. */
public record MiCuentaBancariaDTO(boolean identidadVerificada, CuentaBancariaDTO cuenta) {
}
