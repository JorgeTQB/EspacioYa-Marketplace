package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/** Resultado de una decisión sobre la identidad de un arrendador (HU-06, R27, R31). */
public record RevisionIdentidadDTO(String decision, String estadoIdentidad, Instant fecha) {
}
