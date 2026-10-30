package pe.edu.ulima.espacioya.busqueda.service;

import java.time.LocalDate;

/** Criterios de la búsqueda base, ya validados por {@link BusquedaService}. */
public record CriteriosBusqueda(String tipo, String zona, LocalDate fecha) {
}
