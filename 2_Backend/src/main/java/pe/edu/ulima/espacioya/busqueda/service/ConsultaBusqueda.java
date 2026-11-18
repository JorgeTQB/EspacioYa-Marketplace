package pe.edu.ulima.espacioya.busqueda.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parámetros de {@code GET /api/espacios} tal como llegan, en texto (HU-10 y HU-23). {@code caracteristicas}: cada
 * aparición del parámetro {@code caracteristica}, sin partir por comas; vacío si no vino.
 */
public record ConsultaBusqueda(String tipo, String zona, String fecha, String pagina,
                               String precioMaximo, String capacidadMinima, List<String> caracteristicas) {

    public ConsultaBusqueda {
        caracteristicas = caracteristicas == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(caracteristicas));
    }
}
