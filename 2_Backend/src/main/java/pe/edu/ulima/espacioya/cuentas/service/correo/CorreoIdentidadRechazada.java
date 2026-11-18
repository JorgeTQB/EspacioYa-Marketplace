package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarIdentidadRechazada} (HU-25, tabla C1).
 * El motivo; sin documentos (RNF-SEG-16).
 */
public record CorreoIdentidadRechazada(String motivo) implements Correo {

    public static final String ASUNTO = "Tu identidad no fue verificada";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Revisamos tus documentos y no pudimos verificar tu identidad.

                Motivo: %s

                Puedes volver a subir tus documentos desde tu panel.

                El equipo de EspacioYa
                """.formatted(motivo);
    }
}
