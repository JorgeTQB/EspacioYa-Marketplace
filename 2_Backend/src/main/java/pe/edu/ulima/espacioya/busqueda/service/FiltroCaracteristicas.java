package pe.edu.ulima.espacioya.busqueda.service;

import java.util.Set;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/** ConcreteDecorator (★ HU-23 R14): admite los espacios que tienen todas las características pedidas. */
public final class FiltroCaracteristicas extends FiltroBusqueda {

    private final Set<String> requeridas;

    public FiltroCaracteristicas(BusquedaEspacios envuelta, Set<String> requeridas) {
        super(envuelta);
        if (requeridas == null || requeridas.isEmpty()) {
            throw new IllegalArgumentException("Sin características no hay filtro que aplicar");
        }
        this.requeridas = Set.copyOf(requeridas);
    }

    @Override
    protected boolean admite(EspacioPublicado espacio) {
        return espacio.caracteristicas().containsAll(requeridas);
    }
}
