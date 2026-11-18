package pe.edu.ulima.espacioya.comun.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ★ Adapter sin proveedor (HU-25 D8, D12): no envía nada. Solo deja una línea fija en el log, sin destinatario,
 * asunto ni cuerpo (R35). Es el proveedor por defecto y el de UAT.
 */
@Component
@ConditionalOnProperty(name = "espacioya.correo.proveedor", havingValue = "simulado")
public class EnvioCorreoSimulado implements EnvioCorreo {

    static final String MENSAJE_LOG = "Envío de correo simulado: no se envió ningún correo.";

    private static final Logger log = LoggerFactory.getLogger(EnvioCorreoSimulado.class);

    @Override
    public void enviar(MensajeCorreo mensaje) {
        log.info(MENSAJE_LOG);
    }
}
