package pe.edu.ulima.espacioya.busqueda.service;

import java.util.List;
import java.util.Objects;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/**
 * Decorator (★ HU-23): un filtro de la búsqueda que envuelve a otra {@link BusquedaEspacios} (Component), sea la
 * {@link BusquedaBase} (ConcreteComponent) u otro filtro. Le reenvía {@link #buscar} y conserva, en el mismo orden,
 * los espacios que {@link #admite}; cada ConcreteDecorator solo dice qué admite. No son beans: {@link FiltrosBusqueda}
 * arma la cadena por petición, solo con los filtros presentes.
 */
public abstract class FiltroBusqueda implements BusquedaEspacios {

    private final BusquedaEspacios envuelta;

    protected FiltroBusqueda(BusquedaEspacios envuelta) {
        this.envuelta = Objects.requireNonNull(envuelta, "envuelta");
    }

    @Override
    public final List<EspacioPublicado> buscar(CriteriosBusqueda criterios) {
        return envuelta.buscar(criterios).stream().filter(this::admite).toList();
    }

    protected abstract boolean admite(EspacioPublicado espacio);

    BusquedaEspacios envuelta() {
        return envuelta;
    }
}
