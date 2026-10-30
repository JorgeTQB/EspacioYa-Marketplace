package pe.edu.ulima.espacioya.cuentas.dto;

/** Resultado de suspender o reactivar una cuenta (HU-09, R14, R29, R44): estado nuevo y sesiones revocadas. */
public record CambioEstadoCuentaDTO(String estado, int sesionesRevocadas) {
}
