package pe.edu.ulima.espacioya.busqueda.service;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/** ConcreteDecorator (★ HU-23 R13): admite los espacios con {@code capacidad ≥ capacidadMinima}. */
public final class FiltroCapacidadMinima extends FiltroBusqueda {

    private final int capacidadMinima;

    public FiltroCapacidadMinima(BusquedaEspacios envuelta, int capacidadMinima) {
        super(envuelta);
        this.capacidadMinima = capacidadMinima;
    }

    @Override
    protected boolean admite(EspacioPublicado espacio) {
        return espacio.capacidad() >= capacidadMinima;
    }
}
