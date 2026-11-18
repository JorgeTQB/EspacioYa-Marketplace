package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarReservaPagada} (HU-25, tabla C1).
 * Sin datos del usuario ni del pago (HU-13 D5, RNF-SEG-16).
 */
public record CorreoReservaPagada(AvisoReservaPagada aviso) implements Correo {

    public static final String ASUNTO = "Tienes una reserva pagada";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Se pagó una reserva de tu espacio.

                Espacio: %s
                Fecha: %s
                Horario: %s

                El equipo de EspacioYa
                """.formatted(aviso.espacio(), FormatoCorreo.fecha(aviso.fecha()),
                FormatoCorreo.horario(aviso.horaInicio(), aviso.horaFin()));
    }
}
