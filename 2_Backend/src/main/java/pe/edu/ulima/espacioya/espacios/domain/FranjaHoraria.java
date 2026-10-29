package pe.edu.ulima.espacioya.espacios.domain;

import java.util.Comparator;

/**
 * Franja del horario semanal (HU-08): día ISO (1 = lunes … 7 = domingo) y horas enteras con fin exclusivo
 * ({@code horaFin = 24} es medianoche). Cada hora {@code h} con {@code horaInicio <= h < horaFin} es un bloque.
 */
public record FranjaHoraria(int diaSemana, int horaInicio, int horaFin) implements Comparable<FranjaHoraria> {

    private static final Comparator<FranjaHoraria> ORDEN = Comparator.comparingInt(FranjaHoraria::diaSemana)
            .thenComparingInt(FranjaHoraria::horaInicio)
            .thenComparingInt(FranjaHoraria::horaFin);

    public boolean seSuperponeCon(FranjaHoraria otra) {
        return diaSemana == otra.diaSemana && horaInicio < otra.horaFin && otra.horaInicio < horaFin;
    }

    public boolean contiene(int hora) {
        return horaInicio <= hora && hora < horaFin;
    }

    public String aTexto() {
        return diaSemana + ":" + horaInicio + "-" + horaFin;
    }

    @Override
    public int compareTo(FranjaHoraria otra) {
        return ORDEN.compare(this, otra);
    }
}
