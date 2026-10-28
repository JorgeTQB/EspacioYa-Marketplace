package pe.edu.ulima.espacioya.cuentas.controller;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;

@Configuration
public class ReglasLimiteCuentas {

    public static final int MAXIMO_REGISTROS_POR_IP = 5;
    public static final Duration VENTANA_REGISTROS = Duration.ofHours(1);
    // Los dos registros comparten el cupo por IP (§9.7): con cupos separados serían 10 por hora.
    public static final String GRUPO_REGISTRO = "registro";

    @Bean
    public ReglaLimite reglaLimiteRegistro() {
        return new ReglaLimite("POST", "/api/usuarios", MAXIMO_REGISTROS_POR_IP, VENTANA_REGISTROS, GRUPO_REGISTRO);
    }

    @Bean
    public ReglaLimite reglaLimiteRegistroArrendador() {
        return new ReglaLimite("POST", "/api/arrendadores", MAXIMO_REGISTROS_POR_IP, VENTANA_REGISTROS,
                GRUPO_REGISTRO);
    }
}
