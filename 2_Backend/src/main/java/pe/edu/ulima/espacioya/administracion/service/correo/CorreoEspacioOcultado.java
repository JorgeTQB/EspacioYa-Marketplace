package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;

/**
 * ★ Factory Method · ConcreteProduct: aviso al arrendador de que su espacio se ocultó (HU-27 CA3, R47). Con el motivo
 * de la decisión; sin el motivo del reporte ni datos del reportante (RNF-SEG-16).
 */
public record CorreoEspacioOcultado(DatosEspacioOcultado datos) implements Correo {

    public static final String ASUNTO = "Ocultamos tu espacio en EspacioYa";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        return """
                Hola:

                Revisamos un reporte sobre tu espacio "%s" y decidimos ocultarlo: ya no aparece en la búsqueda ni \
                recibe reservas nuevas.

                Motivo: %s

                %s

                El equipo de EspacioYa
                """.formatted(datos.espacio(), datos.motivo(), reservas());
    }

    private String reservas() {
        if (!datos.porFraude()) {
            return "Las reservas que ya estaban confirmadas se mantienen.";
        }
        if (datos.reservasCanceladas() == 0) {
            return "Como lo ocultamos por fraude, no recibirá más reservas. No tenía reservas confirmadas por empezar.";
        }
        return ("Como lo ocultamos por fraude, cancelamos sus %d reservas confirmadas que aún no empezaban y "
                + "devolvimos el 100 %% a quienes reservaron. Esas reservas no se incluirán en tus liquidaciones.")
                .formatted(datos.reservasCanceladas());
    }
}
