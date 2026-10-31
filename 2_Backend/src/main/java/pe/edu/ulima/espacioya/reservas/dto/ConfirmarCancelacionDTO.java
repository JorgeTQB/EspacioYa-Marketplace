package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;

/**
 * Confirmación (HU-15 CA3). {@code montoReembolsoVisto} solo se compara con el recalculado; nunca es el monto (D5).
 * Sin anotaciones de validación: el servicio valida después del rol y del id (DT-6).
 */
public record ConfirmarCancelacionDTO(BigDecimal montoReembolsoVisto) {
}
