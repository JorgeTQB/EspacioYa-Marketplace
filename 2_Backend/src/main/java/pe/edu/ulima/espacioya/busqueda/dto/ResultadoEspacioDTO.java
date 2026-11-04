package pe.edu.ulima.espacioya.busqueda.dto;

import java.math.BigDecimal;

/** Solo lo que muestra la tarjeta de resultados (RNF-SEG-16): sin dirección ni datos del arrendador (HU-10 R14). */
public record ResultadoEspacioDTO(Long id, String nombre, String zona, String zonaNombre, BigDecimal precioHora,
                                  String fotoPrincipal) {
}
