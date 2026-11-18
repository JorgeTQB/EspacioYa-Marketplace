package pe.edu.ulima.espacioya.busqueda.service;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;

/**
 * ★ Decorator, rol <b>Component</b> (HU-24, DT-20): publicar una reseña. La implementan la regla del negocio
 * ({@link PublicacionResenaBase}) y los decoradores que la envuelven ({@link PublicacionResenaDecorador}). El orden de
 * la cadena lo fija {@link ConfiguracionPublicacionResena#cadena}.
 */
public interface PublicacionResena {

    /** Crea la reseña y la devuelve con su id; lanza las excepciones de {@code comun/error} de R6–R22. */
    Resena publicar(SolicitudResena solicitud);
}
