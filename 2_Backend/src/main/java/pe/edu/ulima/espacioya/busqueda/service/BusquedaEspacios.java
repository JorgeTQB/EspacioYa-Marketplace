package pe.edu.ulima.espacioya.busqueda.service;

import java.util.List;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/**
 * Búsqueda envolvible: HU-23 agrega filtros como decoradores que implementan esta interfaz y envuelven a
 * {@link BusquedaBase}. Devuelve todos los disponibles, ya ordenados, sin paginar.
 */
public interface BusquedaEspacios {

    List<EspacioPublicado> buscar(CriteriosBusqueda criterios);
}
