package pe.edu.ulima.espacioya.busqueda.service;

/**
 * Entrada inmutable de la cadena {@link PublicacionResena} (★ HU-24, DT-24). El usuario ya pasó el control de rol en
 * {@link ResenaService}: basta su id. Un decorador no modifica lo que recibe: entrega a su envuelta una copia.
 */
public record SolicitudResena(Long usuarioId, Long reservaId, int calificacion, String comentario, String ip) {

    /** La misma solicitud con otro comentario (R45). */
    public SolicitudResena conComentario(String otro) {
        return new SolicitudResena(usuarioId, reservaId, calificacion, otro, ip);
    }
}
