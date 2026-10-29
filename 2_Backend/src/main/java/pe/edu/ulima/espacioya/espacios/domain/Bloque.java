package pe.edu.ulima.espacioya.espacios.domain;

/** Bloque de 1 hora de una fecha, de {@code horaInicio} a {@code horaInicio + 1}. */
public record Bloque(int horaInicio, EstadoBloque estado) {

    public int horaFin() {
        return horaInicio + 1;
    }
}
