package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarVerificacion} (HU-25, tabla C1).
 * Sin enlace ni contraseña (D11): la cuenta ya nace verificada (HU-01 S5).
 */
public record CorreoBienvenida(SinDatos evento) implements Correo {

    public static final String ASUNTO = "Bienvenido a EspacioYa";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Tu cuenta de EspacioYa ya está lista. Ya puedes iniciar sesión.

                El equipo de EspacioYa
                """;
    }
}
