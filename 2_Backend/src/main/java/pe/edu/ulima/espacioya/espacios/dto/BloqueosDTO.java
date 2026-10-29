package pe.edu.ulima.espacioya.espacios.dto;

import java.time.LocalDate;
import java.util.List;

/** Ventana de 8 semanas y fechas bloqueadas del espacio desde hoy, ascendentes (HU-08 R15). */
public record BloqueosDTO(LocalDate desde, LocalDate hasta, List<LocalDate> fechas) {
}
