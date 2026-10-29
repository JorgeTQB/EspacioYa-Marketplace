package pe.edu.ulima.espacioya.espacios.domain;

import java.util.ArrayList;
import java.util.List;

/** Reglas de un horario semanal completo que no caben en Bean Validation (HU-08 R5, R6). */
public final class ReglasHorario {

    public enum Problema { RANGO, SUPERPUESTA }

    public record ProblemaFranja(int indice, Problema problema) {
    }

    private ReglasHorario() {
    }

    /**
     * RANGO para cada franja con {@code horaFin <= horaInicio}; si no hay ninguno, SUPERPUESTA para cada franja
     * {@code j} que se superpone con alguna de índice menor. Lista vacía = horario válido.
     */
    public static List<ProblemaFranja> revisar(List<FranjaHoraria> franjas) {
        var problemas = new ArrayList<ProblemaFranja>();
        for (int i = 0; i < franjas.size(); i++) {
            var franja = franjas.get(i);
            if (franja.horaFin() <= franja.horaInicio()) {
                problemas.add(new ProblemaFranja(i, Problema.RANGO));
            }
        }
        if (!problemas.isEmpty()) {
            return problemas;
        }
        for (int j = 1; j < franjas.size(); j++) {
            for (int i = 0; i < j; i++) {
                if (franjas.get(j).seSuperponeCon(franjas.get(i))) {
                    problemas.add(new ProblemaFranja(j, Problema.SUPERPUESTA));
                    break;
                }
            }
        }
        return problemas;
    }
}
