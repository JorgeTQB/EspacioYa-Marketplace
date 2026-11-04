package pe.edu.ulima.espacioya.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Comando de una sola vez para crear una cuenta admin (HU-06, D4). Solo existe con
 * {@code espacioya.admin.crear=true} ({@code ADMIN_CREAR}); crea la cuenta y cierra la aplicación. Si algo falla,
 * la excepción detiene el arranque con un código de salida distinto de 0. El README tiene el comando.
 */
@Component
@ConditionalOnProperty(name = "espacioya.admin.crear", havingValue = "true")
public class CreadorAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CreadorAdmin.class);

    private final CreacionAdminService servicio;
    private final ConfigurableApplicationContext contexto;
    private final String correo;
    private final String contrasena;

    public CreadorAdmin(CreacionAdminService servicio, ConfigurableApplicationContext contexto,
                        @Value("${espacioya.admin.correo:}") String correo,
                        @Value("${espacioya.admin.contrasena:}") String contrasena) {
        this.servicio = servicio;
        this.contexto = contexto;
        this.correo = correo;
        this.contrasena = contrasena;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        servicio.crear(correo, contrasena);
        // Sin el correo: es un dato personal y el log de UAT lo leen más personas (Ley 29733).
        log.info("Cuenta admin creada");
        contexto.close();
    }
}
