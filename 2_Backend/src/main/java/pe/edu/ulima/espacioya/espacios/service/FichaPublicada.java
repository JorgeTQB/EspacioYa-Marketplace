package pe.edu.ulima.espacioya.espacios.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Espacio visible con los datos de su ficha pública, tal como cruza a {@code busqueda} (HU-11). No es un DTO de la
 * API: no lleva arrendadorId, estado, fechas de auditoría ni claves de almacenamiento; {@code fotos} son URL
 * {@code /api/fotos/<UUID>} en orden ascendente.
 */
public record FichaPublicada(Long id, String nombre, String tipo, String direccion, String zona, String zonaNombre,
                             int capacidad, BigDecimal precioHora, String descripcion, List<String> fotos) {
}
