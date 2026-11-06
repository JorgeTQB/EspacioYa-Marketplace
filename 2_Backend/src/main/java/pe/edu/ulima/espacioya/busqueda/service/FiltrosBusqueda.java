package pe.edu.ulima.espacioya.busqueda.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Ensamblador del Decorator (★ HU-23): los filtros ya validados de una búsqueda; {@code null} o vacío = filtro
 * ausente. {@link #envolver} apila sobre la base solo los presentes, en orden fijo; como cada filtro solo quita
 * elementos y conserva el orden, el orden de apilado no cambia el resultado (R15).
 */
public record FiltrosBusqueda(BigDecimal precioMaximo, Integer capacidadMinima, Set<String> caracteristicas) {

    public static final FiltrosBusqueda NINGUNO = new FiltrosBusqueda(null, null, Set.of());

    public FiltrosBusqueda {
        caracteristicas = caracteristicas == null ? Set.of() : Set.copyOf(caracteristicas);
    }

    public boolean vacios() {
        return precioMaximo == null && capacidadMinima == null && caracteristicas.isEmpty();
    }

    /** La base misma si no hay filtros; si no, Caracteristicas( Capacidad( Precio( base ) ) ) con los presentes. */
    public BusquedaEspacios envolver(BusquedaEspacios base) {
        BusquedaEspacios cadena = base;
        if (precioMaximo != null) {
            cadena = new FiltroPrecioMaximo(cadena, precioMaximo);
        }
        if (capacidadMinima != null) {
            cadena = new FiltroCapacidadMinima(cadena, capacidadMinima);
        }
        if (!caracteristicas.isEmpty()) {
            cadena = new FiltroCaracteristicas(cadena, caracteristicas);
        }
        return cadena;
    }
}
