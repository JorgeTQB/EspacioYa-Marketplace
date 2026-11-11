package pe.edu.ulima.espacioya.administracion.dto;

import java.time.Instant;

/**
 * Detalle de un reporte para el admin (HU-27 R24, R25, R45). {@code decision}: MANTENER, OCULTAR, OCULTAR_POR_FRAUDE o
 * {@code null} si está por revisar (R78). {@code espacio} o {@code resena} según el tipo; el otro es {@code null}.
 * {@code reservasCanceladas} solo en la respuesta de la decisión; {@code null} en la consulta. Sin datos del
 * reportante (P14).
 */
public record DetalleReporteAdminDTO(Long id, String tipoObjetivo, Long objetivoId, String motivo, String estado,
                                     Instant creadoEn, String decision, String motivoDecision, Instant resueltoEn,
                                     long reportesPendientes, EspacioReportadoDTO espacio, ResenaReportadaDTO resena,
                                     Integer reservasCanceladas) {
}
