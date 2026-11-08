package pe.edu.ulima.espacioya.comun.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;

/**
 * ★ Adapter · Adapter (HU-25 D8): traduce {@link MensajeCorreo} a un {@link SimpleMailMessage} de texto plano y lo
 * entrega al Adaptee {@link JavaMailSender} (SMTP con STARTTLS, R32). Es la única clase que importa
 * {@code org.springframework.mail}. Cambiar de proveedor SMTP es cambiar variables, no código.
 */
@Component
@ConditionalOnProperty(name = "espacioya.correo.proveedor", havingValue = "smtp")
public class EnvioCorreoSmtp implements EnvioCorreo {

    private static final Logger log = LoggerFactory.getLogger(EnvioCorreoSmtp.class);

    private final JavaMailSender servidor;
    private final String remitente;

    public EnvioCorreoSmtp(JavaMailSender servidor,
                           @Value("${espacioya.correo.remitente:}") String remitente,
                           @Value("${spring.mail.host:}") String host) {
        this.servidor = servidor;
        this.remitente = obligatoria(remitente, "CORREO_REMITENTE");
        obligatoria(host, "CORREO_SMTP_HOST");
    }

    @Override
    public void enviar(MensajeCorreo mensaje) {
        var correo = new SimpleMailMessage();
        correo.setFrom(remitente);
        correo.setTo(mensaje.para());
        correo.setSubject(mensaje.asunto());
        correo.setText(mensaje.cuerpo());
        try {
            servidor.send(correo);
        } catch (MailException e) {
            // Sin el destinatario ni el texto del proveedor (R33): el error de SMTP puede citar la dirección.
            log.warn("El servidor SMTP no aceptó un correo ({})", e.getClass().getSimpleName());
            throw new ServicioExternoException();
        }
    }

    private static String obligatoria(String valor, String variable) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta " + variable + ": el proveedor de correo smtp la necesita");
        }
        return valor.strip();
    }
}
