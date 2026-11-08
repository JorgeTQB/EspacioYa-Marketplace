package pe.edu.ulima.espacioya.busqueda.dto;

import java.util.List;

public record PaginaResenasDTO(int pagina, List<ResenaPublicaDTO> resenas, boolean hayMas) {
}
