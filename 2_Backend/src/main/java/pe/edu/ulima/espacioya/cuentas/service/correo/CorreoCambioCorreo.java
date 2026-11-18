package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.cuentas.domain.Token;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarCambioCorreo} (HU-25, tabla C1).
 * El enlace al correo nuevo (HU-20 R26).
 */
public record CorreoCambioCorreo(String enlace) implements Correo {

    public static final String ASUNTO = "Confirma tu nuevo correo";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Pediste usar este correo en tu cuenta de EspacioYa. Para confirmarlo, abre este enlace:

                %s

                El enlace vence en %d minutos. Si no lo pediste, ignora este correo.

                El equipo de EspacioYa
                """.formatted(enlace, Token.VIGENCIA_CAMBIO_CORREO.toMinutes());
    }
}
