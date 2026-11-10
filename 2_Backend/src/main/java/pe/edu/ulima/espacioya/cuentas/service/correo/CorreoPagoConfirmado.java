package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarPagoConfirmado} (HU-25, tabla C1).
 * De la tarjeta solo los últimos 4, si los hay (HU-13 D5); nunca el número completo.
 */
public record CorreoPagoConfirmado(ComprobantePago comprobante) implements Correo {

    public static final String ASUNTO = "Comprobante de tu reserva";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        var tarjeta = comprobante.ultimos4() == null || comprobante.ultimos4().isBlank()
                ? ""
                : "\nTarjeta terminada en: " + comprobante.ultimos4();
        return """
                Hola:

                Recibimos el pago de tu reserva. Este es tu comprobante.

                Espacio: %s
                Fecha: %s
                Horario: %s
                Monto: %s
                Referencia: %s%s

                El equipo de EspacioYa
                """.formatted(comprobante.espacio(), FormatoCorreo.fecha(comprobante.fecha()),
                FormatoCorreo.horario(comprobante.horaInicio(), comprobante.horaFin()),
                FormatoCorreo.monto(comprobante.monto()), comprobante.referencia(), tarjeta);
    }
}
