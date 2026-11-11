package pe.edu.ulima.espacioya.espacios.service;

import java.math.BigDecimal;

/** Un espacio en su estado actual, tal como lo revisa el admin (HU-27 R24). Sin datos de contacto del arrendador. */
public record EspacioModerado(Long id, Long arrendadorId, String nombre, String tipo, String zona, String direccion,
                              int capacidad, BigDecimal precioHora, String descripcion, String estado) {
}
