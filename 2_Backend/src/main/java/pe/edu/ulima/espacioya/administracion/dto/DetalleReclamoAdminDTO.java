package pe.edu.ulima.espacioya.administracion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalle de un reclamo para el admin (HU-26 R37): los nombres de las partes y los montos, sin correo ni teléfono ni
 * datos de la tarjeta.
 */
public record DetalleReclamoAdminDTO(Long id, Long reservaId, String espacioNombre, LocalDate fecha, int horaInicio,
                                     int horaFin, String motivo, String descripcion, String estado, Instant abiertoEn,
                                     List<MensajeReclamoDTO> mensajes, String aFavorDe, String resolucion,
                                     Instant resueltoEn, BigDecimal montoReembolso, String usuarioNombre,
                                     String arrendadorNombre, BigDecimal montoPagado) {
}
