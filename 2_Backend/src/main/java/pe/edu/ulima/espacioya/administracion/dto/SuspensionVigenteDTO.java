package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Motivo y fecha de la última suspensión de una cuenta suspendida (HU-09, R10). */
public record SuspensionVigenteDTO(String motivo, Instant fecha) {
}
