package pe.edu.ulima.espacioya.administracion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalle de un reclamo para una de sus partes (HU-26 R2, R32). {@code montoReembolso} va solo al usuario; al
 * arrendador nunca llegan montos ni el nombre, correo o teléfono del usuario.
 */
public record DetalleReclamoDTO(Long id, Long reservaId, String espacioNombre, LocalDate fecha, int horaInicio,
                                int horaFin, String motivo, String descripcion, String estado, Instant abiertoEn,
                                List<MensajeReclamoDTO> mensajes, String aFavorDe, String resolucion,
                                Instant resueltoEn, BigDecimal montoReembolso, String papel, int mensajesRestantes,
                                boolean puedeEscribir) {
}
