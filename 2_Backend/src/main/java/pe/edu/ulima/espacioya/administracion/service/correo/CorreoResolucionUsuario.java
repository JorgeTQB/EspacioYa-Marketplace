package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;

/** ★ Factory Method · ConcreteProduct: resolución del reclamo para el usuario que lo abrió (HU-26 R57). */
public record CorreoResolucionUsuario(DatosResolucion datos) implements Correo {

    public static final String ASUNTO = "Resolvimos tu reclamo";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        var aSuFavor = datos.aFavorDe() == PapelReclamo.USUARIO;
        var resultado = aSuFavor ? "a tu favor" : "a favor del arrendador";
        var reembolso = aSuFavor
                ? "\nSolicitamos un reembolso de " + FormatoCorreo.monto(datos.montoReembolso())
                        + " a tu medio de pago.\n"
                : "";
        return """
                Hola:

                Resolvimos el reclamo que abriste sobre tu reserva.

                Espacio: %s
                Fecha: %s
                Horario: %s
                Resultado: %s
                Motivo: %s
                %s
                El equipo de EspacioYa
                """.formatted(datos.espacio(), FormatoCorreo.fecha(datos.fecha()),
                FormatoCorreo.horario(datos.horaInicio(), datos.horaFin()), resultado, datos.resolucion(), reembolso);
    }
}
