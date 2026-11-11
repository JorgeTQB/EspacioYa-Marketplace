package pe.edu.ulima.espacioya.administracion.dto;

import java.util.List;

/** Una página de 10 reportes de la cola del admin (HU-27 R20, R21). */
public record PaginaReportesDTO(int pagina, List<ReporteResumenDTO> reportes, boolean hayMas) {
}
