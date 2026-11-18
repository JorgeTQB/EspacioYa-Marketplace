package pe.edu.ulima.espacioya.busqueda.service;

import java.util.List;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/**
 * Component del Decorator (★ HU-23): {@link BusquedaBase} es el ConcreteComponent y cada {@link FiltroBusqueda} la
 * envuelve. Devuelve todos los disponibles, ya ordenados, sin paginar.
 */
public interface BusquedaEspacios {

    List<EspacioPublicado> buscar(CriteriosBusqueda criterios);
}
