package pe.edu.ulima.espacioya.busqueda.service;

import java.math.BigDecimal;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/** ConcreteDecorator (★ HU-23 R12): admite los espacios con {@code precioHora ≤ precioMaximo}. */
public final class FiltroPrecioMaximo extends FiltroBusqueda {

    private final BigDecimal precioMaximo;

    public FiltroPrecioMaximo(BusquedaEspacios envuelta, BigDecimal precioMaximo) {
        super(envuelta);
        if (precioMaximo == null) {
            throw new IllegalArgumentException("precioMaximo");
        }
        this.precioMaximo = precioMaximo;
    }

    @Override
    protected boolean admite(EspacioPublicado espacio) {
        return espacio.precioHora().compareTo(precioMaximo) <= 0;
    }
}
