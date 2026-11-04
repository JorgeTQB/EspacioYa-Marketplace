package pe.edu.ulima.espacioya.reservas.domain;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Iterador concreto de {@link HistorialReservas}: un cursor sobre la copia ordenada, en un sentido y con un criterio.
 * {@code hasNext} busca el siguiente que cumple sin consumirlo. {@code remove} no está soportado (el default lanza).
 */
final class IteradorReservas implements Iterator<ReservaConHorario> {

    private final List<ReservaConHorario> ordenadas;
    private final boolean ascendente;
    private final Predicate<ReservaConHorario> criterio;
    private int pasos;

    IteradorReservas(List<ReservaConHorario> ordenadas, boolean ascendente, Predicate<ReservaConHorario> criterio) {
        this.ordenadas = ordenadas;
        this.ascendente = ascendente;
        this.criterio = criterio;
    }

    @Override
    public boolean hasNext() {
        while (pasos < ordenadas.size()) {
            if (criterio.test(actual())) {
                return true;
            }
            pasos++;
        }
        return false;
    }

    @Override
    public ReservaConHorario next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        var siguiente = actual();
        pasos++;
        return siguiente;
    }

    private ReservaConHorario actual() {
        return ordenadas.get(ascendente ? pasos : ordenadas.size() - 1 - pasos);
    }
}
