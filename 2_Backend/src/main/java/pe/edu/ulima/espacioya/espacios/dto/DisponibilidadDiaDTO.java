package pe.edu.ulima.espacioya.espacios.dto;

import java.time.LocalDate;
import java.util.List;

/** Bloques de una fecha con su estado (HU-08 R22). Sin datos de las reservas (R45). */
public record DisponibilidadDiaDTO(LocalDate fecha, boolean bloqueada, List<BloqueDTO> bloques) {
}
