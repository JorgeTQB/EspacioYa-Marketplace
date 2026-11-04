package pe.edu.ulima.espacioya.espacios.dto;

import java.math.BigDecimal;

/**
 * Fila del listado de espacios propios (HU-07 R9).
 */
public record EspacioResumenDTO(Long id, String nombre, String tipo, String zona, String zonaNombre,
                                BigDecimal precioHora, String estado) {
}
