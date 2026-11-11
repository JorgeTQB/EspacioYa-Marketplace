package pe.edu.ulima.espacioya.espacios.service;

/** Un espacio visible que se puede reportar (HU-27): basta su arrendador para saber si es contenido propio. */
public record EspacioReportable(Long id, Long arrendadorId) {
}
