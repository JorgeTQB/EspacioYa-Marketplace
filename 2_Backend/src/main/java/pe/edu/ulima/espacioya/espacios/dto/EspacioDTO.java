package pe.edu.ulima.espacioya.espacios.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detalle de un espacio propio (HU-07 R10). Sin arrendador, contacto ni claves de almacenamiento (R7).
 */
public record EspacioDTO(Long id, String nombre, String tipo, String direccion, String zona, String zonaNombre,
                         int capacidad, BigDecimal precioHora, String descripcion, String estado,
                         Instant creadoEn, Instant actualizadoEn, List<FotoEspacioDTO> fotos) {
}
