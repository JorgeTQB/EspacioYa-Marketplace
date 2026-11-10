package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarIdentidadAprobada} (HU-25, tabla C1).
 * Sin documentos (RNF-SEG-16).
 */
public record CorreoIdentidadAprobada(SinDatos evento) implements Correo {

    public static final String ASUNTO = "Tu identidad fue verificada";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Revisamos tus documentos y tu identidad quedó verificada. Ya puedes publicar espacios.

                El equipo de EspacioYa
                """;
    }
}
