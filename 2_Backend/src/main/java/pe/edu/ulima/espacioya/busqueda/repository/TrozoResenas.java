package pe.edu.ulima.espacioya.busqueda.repository;

import java.util.List;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;

/** TransferObject de {@link ResenaDAO#visiblesDeEspacio}: una página de reseñas y si hay más después (★ HU-24). */
public record TrozoResenas(List<Resena> resenas, boolean hayMas) {

    public static final TrozoResenas VACIO = new TrozoResenas(List.of(), false);

    public TrozoResenas {
        resenas = List.copyOf(resenas);
    }
}
