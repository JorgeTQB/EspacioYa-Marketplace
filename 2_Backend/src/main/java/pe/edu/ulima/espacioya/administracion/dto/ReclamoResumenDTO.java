package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Fila de las listas de reclamos (HU-26 R31, R35, R36). */
public record ReclamoResumenDTO(Long id, Long reservaId, String espacioNombre, LocalDate fecha, String motivo,
                                String estado, Instant abiertoEn, Instant resueltoEn) {
}
