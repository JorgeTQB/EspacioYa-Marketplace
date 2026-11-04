package pe.edu.ulima.espacioya.busqueda.dto;

import java.util.List;

/** Una página de la búsqueda pública (HU-10): sin total, para no revelar el tamaño del catálogo (DT-8). */
public record ResultadoBusquedaDTO(int pagina, List<ResultadoEspacioDTO> resultados, boolean hayMas) {

    public ResultadoBusquedaDTO {
        resultados = List.copyOf(resultados);
    }
}
