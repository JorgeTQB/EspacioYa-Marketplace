package pe.edu.ulima.espacioya.cuentas.controller;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;

@Configuration
public class ReglasLimiteCuentas {

    public static final int MAXIMO_REGISTROS_POR_IP = 5;
    public static final Duration VENTANA_REGISTROS = Duration.ofHours(1);

    @Bean
    public ReglaLimite reglaLimiteRegistro() {
        return new ReglaLimite("POST", "/api/usuarios", MAXIMO_REGISTROS_POR_IP, VENTANA_REGISTROS);
    }
}
