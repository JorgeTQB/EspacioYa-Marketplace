package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarCancelacionAlArrendador} (HU-25, tabla C1).
 * Sin datos del usuario ni del reembolso (HU-15 D8, RNF-SEG-16).
 */
public record CorreoCancelacionArrendador(AvisoCancelacionArrendador aviso) implements Correo {

    public static final String ASUNTO = "Se canceló una reserva de tu espacio";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Se canceló una reserva de tu espacio.

                Espacio: %s
                Fecha: %s
                Horario: %s
                Cancelada por: %s

                El equipo de EspacioYa
                """.formatted(aviso.espacio(), FormatoCorreo.fecha(aviso.fecha()),
                FormatoCorreo.horario(aviso.horaInicio(), aviso.horaFin()), QuienCancelo.texto(aviso.canceladaPor()));
    }
}
