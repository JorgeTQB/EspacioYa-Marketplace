package pe.edu.ulima.espacioya.reservas.domain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Una página de un recorrido (HU-14 D3): sin total; {@code hayMas} sale del propio iterador. */
public record Pagina<T>(int numero, List<T> elementos, boolean hayMas) {

    public Pagina {
        elementos = List.copyOf(elementos);
    }

    /**
     * Salta {@code (numero − 1) × tamano} con aritmética long, toma hasta {@code tamano} y {@code hayMas =
     * recorrido.hasNext()} (R26, R27, R32). No materializa el resto del recorrido.
     */
    public static <T> Pagina<T> de(Iterator<T> recorrido, int numero, int tamano) {
        if (numero < 1 || tamano < 1) {
            throw new IllegalArgumentException("Página " + numero + " o tamaño " + tamano + " no válidos");
        }
        var salto = (long) (numero - 1) * tamano;
        for (long saltados = 0; saltados < salto && recorrido.hasNext(); saltados++) {
            recorrido.next();
        }
        var elementos = new ArrayList<T>(tamano);
        while (elementos.size() < tamano && recorrido.hasNext()) {
            elementos.add(recorrido.next());
        }
        return new Pagina<>(numero, elementos, recorrido.hasNext());
    }
}
