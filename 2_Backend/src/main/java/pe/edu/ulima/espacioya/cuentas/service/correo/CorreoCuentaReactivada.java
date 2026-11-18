package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarCuentaReactivada} (HU-25, tabla C1).
 * Solo el aviso (HU-09).
 */
public record CorreoCuentaReactivada(SinDatos evento) implements Correo {

    public static final String ASUNTO = "Tu cuenta fue reactivada";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Tu cuenta de EspacioYa fue reactivada. Ya puedes iniciar sesión.

                El equipo de EspacioYa
                """;
    }
}
