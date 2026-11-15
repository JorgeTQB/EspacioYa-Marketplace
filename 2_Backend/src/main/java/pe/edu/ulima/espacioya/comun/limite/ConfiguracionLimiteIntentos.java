package pe.edu.ulima.espacioya.comun.limite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El limitador de la aplicación cuenta en PostgreSQL: los límites valen para todas las réplicas (HU-30). */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionLimiteIntentos {

    @Bean
    public LimitadorIntentos limitadorIntentos(ConteoIntentosPostgres conteo) {
        return new LimitadorIntentos(conteo);
    }
}
