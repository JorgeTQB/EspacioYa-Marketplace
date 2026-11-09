package pe.edu.ulima.espacioya.administracion.service.correo;

import java.math.BigDecimal;
import java.time.LocalDate;

import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;

/**
 * Evento "se resolvió un reclamo" (HU-26 R57, R58). {@code montoReembolso} solo con {@code aFavorDe = USUARIO}; el
 * correo al arrendador no lo usa (P12).
 */
public record DatosResolucion(String espacio, LocalDate fecha, int horaInicio, int horaFin, PapelReclamo aFavorDe,
                              String resolucion, BigDecimal montoReembolso) {
}
