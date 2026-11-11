package pe.edu.ulima.espacioya.busqueda.service;

/** Una reseña visible de un espacio visible que se puede reportar (HU-27); el autor decide si es contenido propio. */
public record ResenaReportable(Long id, Long autorId, Long espacioId) {
}
