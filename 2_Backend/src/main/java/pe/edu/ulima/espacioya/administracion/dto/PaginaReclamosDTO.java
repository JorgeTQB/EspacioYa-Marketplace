package pe.edu.ulima.espacioya.administracion.dto;

import java.util.List;

/** Una página de reclamos, de a 10, con {@code hayMas} y sin total (HU-26 R31, R35, R36). */
public record PaginaReclamosDTO(int pagina, List<ReclamoResumenDTO> reclamos, boolean hayMas) {
}
