package pe.edu.ulima.espacioya.espacios.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detalle de un espacio propio (HU-07 R10). Sin arrendador, contacto ni claves de almacenamiento (R7).
 * {@code caracteristicas}: códigos en el orden de la lista fija, nunca null (HU-23 R6).
 */
public record EspacioDTO(Long id, String nombre, String tipo, String direccion, String zona, String zonaNombre,
                         int capacidad, BigDecimal precioHora, String descripcion, String estado,
                         Instant creadoEn, Instant actualizadoEn, List<FotoEspacioDTO> fotos,
                         List<String> caracteristicas) {
}
