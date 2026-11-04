package pe.edu.ulima.espacioya.busqueda.dto;

import java.math.BigDecimal;
import java.util.List;

import pe.edu.ulima.espacioya.espacios.service.FichaPublicada;

/**
 * Ficha pública de un espacio (HU-11 R2, R7, RNF-SEG-16): sin arrendadorId, estado, fechas ni claves de
 * almacenamiento, y nada del arrendador. {@code fotos} son URL {@code /api/fotos/<UUID>} en orden.
 */
public record FichaPublicaDTO(Long id, String nombre, String tipo, String direccion, String zona, String zonaNombre,
                              int capacidad, BigDecimal precioHora, String descripcion, List<String> fotos) {

    public static FichaPublicaDTO de(FichaPublicada ficha) {
        return new FichaPublicaDTO(ficha.id(), ficha.nombre(), ficha.tipo(), ficha.direccion(), ficha.zona(),
                ficha.zonaNombre(), ficha.capacidad(), ficha.precioHora(), ficha.descripcion(),
                List.copyOf(ficha.fotos()));
    }
}
