package pe.edu.ulima.espacioya.busqueda.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;

/**
 * La ficha y su disponibilidad comparten el cupo del catálogo con la búsqueda (HU-11 DT-11, §9.9). Son dos reglas
 * porque el patrón {@code /api/espacios/{id}} no cubre la subruta.
 */
@Configuration
public class ReglasLimiteFicha {

    public static final String RUTA_FICHA = "/api/espacios/{id}";
    public static final String RUTA_DISPONIBILIDAD = "/api/espacios/{id}/disponibilidad";

    @Bean
    public ReglaLimite reglaLimiteFicha(LimiteCatalogo limite) {
        return limite.regla("GET", RUTA_FICHA);
    }

    @Bean
    public ReglaLimite reglaLimiteDisponibilidad(LimiteCatalogo limite) {
        return limite.regla("GET", RUTA_DISPONIBILIDAD);
    }
}
