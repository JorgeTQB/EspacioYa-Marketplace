package pe.edu.ulima.espacioya.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificadorCorreoSimulado implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoSimulado.class);

    @Override
    public void notificarVerificacion(String correoNormalizado) {
        // El correo no va al log: es un dato personal (Ley 29733). HU-25 conecta el envío real.
        log.info("Envío de correo de verificación simulado: no se envió ningún correo (HU-01).");
    }
}
