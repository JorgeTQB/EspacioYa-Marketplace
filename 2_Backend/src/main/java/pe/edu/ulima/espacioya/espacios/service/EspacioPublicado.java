package pe.edu.ulima.espacioya.espacios.service;

import java.math.BigDecimal;

/** Espacio visible en el catálogo público, tal como cruza a {@code busqueda} (HU-10). No es un DTO de la API. */
public record EspacioPublicado(Long id, String nombre, String tipo, String zona, String zonaNombre,
                               BigDecimal precioHora) {
}
