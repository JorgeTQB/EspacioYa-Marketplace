package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarCuentaSuspendida} (HU-25, tabla C1).
 * El motivo de la suspensión (HU-09).
 */
public record CorreoCuentaSuspendida(String motivo) implements Correo {

    public static final String ASUNTO = "Tu cuenta fue suspendida";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Tu cuenta de EspacioYa fue suspendida.

                Motivo: %s

                El equipo de EspacioYa
                """.formatted(motivo);
    }
}
