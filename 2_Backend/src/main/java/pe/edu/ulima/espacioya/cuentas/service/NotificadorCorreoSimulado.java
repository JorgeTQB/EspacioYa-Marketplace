package pe.edu.ulima.espacioya.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificadorCorreoSimulado implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoSimulado.class);

    private final boolean registrarEnlace;

    public NotificadorCorreoSimulado(
            @Value("${espacioya.correo.simulado.registrar-enlace:false}") boolean registrarEnlace) {
        this.registrarEnlace = registrarEnlace;
    }

    @Override
    public void notificarVerificacion(String correoNormalizado) {
        // El correo no va al log: es un dato personal (Ley 29733). HU-25 conecta el envío real.
        log.info("Envío de correo de verificación simulado: no se envió ningún correo (HU-01).");
    }

    // Sin envío real, el enlace solo se puede probar si se lee del log. Por eso se registra únicamente con la
    // propiedad de desarrollo activada (apagada por defecto y nunca en UAT) y nunca junto al correo (HU-04, D1).
    @Override
    public void notificarRecuperacion(String correoNormalizado, String enlace) {
        if (registrarEnlace) {
            log.info("[Solo desarrollo] Enlace de recuperación simulado: {}", enlace);
            return;
        }
        log.info("Envío de correo de recuperación simulado: no se envió ningún correo (HU-04).");
    }
}
