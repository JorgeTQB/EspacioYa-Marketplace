package pe.edu.ulima.espacioya.espacios.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** Bloques de un día = horario − bloqueos − reservas − horas ya empezadas, calculados al consultar (HU-08 DT-8). */
public final class CalculoDisponibilidad {

    private CalculoDisponibilidad() {
    }

    /**
     * Un Bloque por cada hora de las franjas del día de la semana ISO de {@code fecha}, ascendente. Precedencia:
     * BLOQUEADO (si bloqueada) > RESERVADO (hora en horasReservadas) > PASADO ({@code fecha.atTime(h, 0)} no es
     * posterior a ahoraLocal) > LIBRE. No consulta nada: el llamador ya filtró las franjas y las reservas.
     */
    public static List<Bloque> bloquesDelDia(LocalDate fecha, List<FranjaHoraria> franjasDelDia, boolean bloqueada,
                                             Set<Integer> horasReservadas, LocalDateTime ahoraLocal) {
        var dia = fecha.getDayOfWeek().getValue();
        return franjasDelDia.stream()
                .filter(franja -> franja.diaSemana() == dia)
                .sorted()
                .flatMap(franja -> IntStream.range(franja.horaInicio(), franja.horaFin()).boxed())
                .distinct()
                .sorted()
                .map(hora -> new Bloque(hora, estado(fecha, hora, bloqueada, horasReservadas, ahoraLocal)))
                .toList();
    }

    private static EstadoBloque estado(LocalDate fecha, int hora, boolean bloqueada, Set<Integer> horasReservadas,
                                       LocalDateTime ahoraLocal) {
        if (bloqueada) {
            return EstadoBloque.BLOQUEADO;
        }
        if (horasReservadas.contains(hora)) {
            return EstadoBloque.RESERVADO;
        }
        if (!fecha.atTime(hora, 0).isAfter(ahoraLocal)) {
            return EstadoBloque.PASADO;
        }
        return EstadoBloque.LIBRE;
    }
}
