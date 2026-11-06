package pe.edu.ulima.espacioya.espacios.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Espacio visible en el catálogo público, tal como cruza a {@code busqueda} (HU-10). No es un DTO de la API.
 * HU-23: lleva la capacidad y los códigos de sus características para los filtros de la búsqueda.
 */
public record EspacioPublicado(Long id, String nombre, String tipo, String zona, String zonaNombre,
                               BigDecimal precioHora, int capacidad, Set<String> caracteristicas) {

    public EspacioPublicado {
        caracteristicas = Set.copyOf(caracteristicas);
    }
}
