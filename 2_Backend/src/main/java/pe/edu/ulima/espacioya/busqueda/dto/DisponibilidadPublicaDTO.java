package pe.edu.ulima.espacioya.busqueda.dto;

import java.time.LocalDate;
import java.util.List;

/** Solo los bloques libres del día (HU-11 R13, R19): ni los ocupados ni por qué no están libres. */
public record DisponibilidadPublicaDTO(LocalDate fecha, List<BloqueLibreDTO> bloquesLibres) {
}
