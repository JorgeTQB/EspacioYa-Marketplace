package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;

/**
 * ★ Factory Method · ConcreteProduct (HU-25): aviso al arrendador de que se abrió un reclamo sobre una reserva de su
 * espacio (HU-26 R22). Sin la descripción ni el nombre, correo o teléfono del usuario.
 */
public record CorreoReclamoAbierto(DatosReclamoAbierto datos) implements Correo {

    public static final String ASUNTO = "Recibiste un reclamo sobre una reserva";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Un usuario abrió un reclamo sobre una reserva de tu espacio.

                Espacio: %s
                Fecha: %s
                Horario: %s
                Motivo: %s

                Entra a EspacioYa, en Reclamos, para responder. El cobro de esta reserva queda retenido hasta que \
                el reclamo se resuelva.

                El equipo de EspacioYa
                """.formatted(datos.espacio(), FormatoCorreo.fecha(datos.fecha()),
                FormatoCorreo.horario(datos.horaInicio(), datos.horaFin()),
                DatosReclamoAbierto.etiqueta(datos.motivo()));
    }
}
