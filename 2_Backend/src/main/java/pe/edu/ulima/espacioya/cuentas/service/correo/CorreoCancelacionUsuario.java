package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarCancelacionAlUsuario} (HU-25, tabla C1).
 * Quién canceló, porcentaje y monto del reembolso (HU-15 D8); sin datos de la tarjeta.
 */
public record CorreoCancelacionUsuario(AvisoCancelacionUsuario aviso) implements Correo {

    public static final String ASUNTO = "Tu reserva fue cancelada";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Tu reserva fue cancelada.

                Espacio: %s
                Fecha: %s
                Horario: %s
                Cancelada por: %s
                Reembolso: %d %% (%s)

                El equipo de EspacioYa
                """.formatted(aviso.espacio(), FormatoCorreo.fecha(aviso.fecha()),
                FormatoCorreo.horario(aviso.horaInicio(), aviso.horaFin()), QuienCancelo.texto(aviso.canceladaPor()),
                aviso.porcentaje(), FormatoCorreo.monto(aviso.montoReembolso()));
    }
}
