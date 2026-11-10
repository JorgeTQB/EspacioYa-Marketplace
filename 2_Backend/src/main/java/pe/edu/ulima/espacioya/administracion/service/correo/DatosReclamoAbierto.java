package pe.edu.ulima.espacioya.administracion.service.correo;

import java.time.LocalDate;

import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;

/**
 * Evento "se abrió un reclamo" para el arrendador (HU-26 R22): sin la descripción ni datos del usuario (RNF-SEG-16).
 */
public record DatosReclamoAbierto(String espacio, LocalDate fecha, int horaInicio, int horaFin, MotivoReclamo motivo) {

    /** Etiqueta en español del motivo, como la muestra la pantalla. */
    public static String etiqueta(MotivoReclamo motivo) {
        return switch (motivo) {
            case ESPACIO_CERRADO -> "Espacio cerrado";
            case DISTINTO_A_LO_PUBLICADO -> "Distinto a lo publicado";
            case OTRO -> "Otro";
        };
    }
}
