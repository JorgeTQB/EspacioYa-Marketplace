package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarAvisoCambioCorreo} (HU-25, tabla C1).
 * Solo el correo nuevo enmascarado (HU-20 R27, D2), nunca completo.
 */
public record CorreoAvisoCambioCorreo(String correoNuevoEnmascarado) implements Correo {

    public static final String ASUNTO = "Se pidió cambiar el correo de tu cuenta";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Se pidió cambiar el correo de tu cuenta de EspacioYa a %s.
                El cambio solo se hace cuando se confirma desde el correo nuevo.

                Si no fuiste tú, cambia tu contraseña.

                El equipo de EspacioYa
                """.formatted(correoNuevoEnmascarado);
    }
}
