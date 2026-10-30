package pe.edu.ulima.espacioya.busqueda.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;

@Configuration
public class ReglasLimiteBusqueda {

    public static final String RUTA_BUSQUEDA = "/api/espacios";

    @Bean
    public ReglaLimite reglaLimiteBusqueda(LimiteCatalogo limite) {
        return limite.regla("GET", RUTA_BUSQUEDA);
    }
}
