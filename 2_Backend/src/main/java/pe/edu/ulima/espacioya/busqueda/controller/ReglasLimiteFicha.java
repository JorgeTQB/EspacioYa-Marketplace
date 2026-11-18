package pe.edu.ulima.espacioya.busqueda.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;

/**
 * La ficha, su disponibilidad y sus reseñas comparten el cupo del catálogo con la búsqueda (HU-11 DT-11, HU-24 R37,
 * §9.9). Son reglas separadas porque el patrón {@code /api/espacios/{id}} no cubre las subrutas.
 */
@Configuration
public class ReglasLimiteFicha {

    public static final String RUTA_FICHA = "/api/espacios/{id}";
    public static final String RUTA_DISPONIBILIDAD = "/api/espacios/{id}/disponibilidad";
    public static final String RUTA_RESENAS = "/api/espacios/{id}/resenas";

    @Bean
    public ReglaLimite reglaLimiteFicha(LimiteCatalogo limite) {
        return limite.regla("GET", RUTA_FICHA);
    }

    @Bean
    public ReglaLimite reglaLimiteDisponibilidad(LimiteCatalogo limite) {
        return limite.regla("GET", RUTA_DISPONIBILIDAD);
    }

    @Bean
    public ReglaLimite reglaLimiteResenas(LimiteCatalogo limite) {
        return limite.regla("GET", RUTA_RESENAS);
    }
}
