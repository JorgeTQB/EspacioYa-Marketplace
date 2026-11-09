package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;

/**
 * ★ Factory Method · ConcreteProduct: resolución del reclamo para el arrendador (HU-26 R58). Sin montos ni datos del
 * usuario (P12).
 */
public record CorreoResolucionArrendador(DatosResolucion datos) implements Correo {

    public static final String ASUNTO = "Resolvimos un reclamo sobre tu espacio";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        var resultado = datos.aFavorDe() == PapelReclamo.USUARIO
                ? "a favor del usuario. Esta reserva no se liquidará."
                : "a tu favor. La reserva entra en la siguiente liquidación.";
        return """
                Hola:

                Resolvimos el reclamo sobre una reserva de tu espacio.

                Espacio: %s
                Fecha: %s
                Horario: %s
                Resultado: %s
                Motivo: %s

                El equipo de EspacioYa
                """.formatted(datos.espacio(), FormatoCorreo.fecha(datos.fecha()),
                FormatoCorreo.horario(datos.horaInicio(), datos.horaFin()), resultado, datos.resolucion());
    }
}
