package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;

/** ★ Factory Method · ConcreteProduct: recordatorio a quien reservó (HU-25, tabla C1, R29). */
public record CorreoRecordatorioUsuario(DatosRecordatorio datos) implements Correo {

    public static final String ASUNTO = "Recordatorio: tu reserva es mañana";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Te recordamos que tu reserva en EspacioYa empieza en menos de 24 horas.

                Espacio: %s
                Fecha: %s
                Horario: %s

                El equipo de EspacioYa
                """.formatted(datos.espacio(), FormatoCorreo.fecha(datos.fecha()),
                FormatoCorreo.horario(datos.horaInicio(), datos.horaFin()));
    }
}
