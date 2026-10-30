package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Resultado de suspender o reactivar una cuenta (HU-09, R14, R29). */
public record AccionCuentaDTO(String accion, String estado, Instant fecha) {
}
