package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.cuentas.domain.Token;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarRecuperacion} (HU-25, tabla C1).
 * El enlace y su vencimiento; nunca la contraseña.
 */
public record CorreoRecuperacion(String enlace) implements Correo {

    public static final String ASUNTO = "Recupera tu contraseña de EspacioYa";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Recibimos un pedido para cambiar la contraseña de tu cuenta. Para elegir una nueva, abre este enlace:

                %s

                El enlace vence en %d minutos. Si no lo pediste, ignora este correo.

                El equipo de EspacioYa
                """.formatted(enlace, Token.VIGENCIA_RECUPERACION.toMinutes());
    }
}
