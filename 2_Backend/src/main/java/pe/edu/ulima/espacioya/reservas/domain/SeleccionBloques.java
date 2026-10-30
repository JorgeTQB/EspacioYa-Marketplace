package pe.edu.ulima.espacioya.reservas.domain;

import java.util.List;
import java.util.Objects;

/** Regla de una selección de horas de un mismo día: de 1 a 24 horas de 0 a 23, sin repetir y consecutivas (RN-05). */
public final class SeleccionBloques {

    private static final int MAXIMO_HORAS = 24;
    private static final int ULTIMA_HORA = 23;

    private SeleccionBloques() {
    }

    public static boolean esValida(List<Integer> horas) {
        if (horas == null || horas.isEmpty() || horas.size() > MAXIMO_HORAS
                || horas.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        var ordenadas = ordenadas(horas);
        if (ordenadas.getFirst() < 0 || ordenadas.getLast() > ULTIMA_HORA) {
            return false;
        }
        // Sin repetidas y sin huecos, la distancia entre la primera y la última es exactamente el número de pasos.
        var sinRepetir = ordenadas.stream().distinct().count() == ordenadas.size();
        return sinRepetir && ordenadas.getLast() - ordenadas.getFirst() == ordenadas.size() - 1;
    }

    public static List<Integer> ordenadas(List<Integer> horas) {
        return horas.stream().sorted().toList();
    }
}
