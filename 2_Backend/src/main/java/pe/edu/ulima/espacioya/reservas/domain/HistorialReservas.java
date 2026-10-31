package pe.edu.ulima.espacioya.reservas.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Agregado del Iterator (★ HU-14): recorre las reservas próximas y pasadas sin exponer la colección. Guarda una copia
 * privada ordenada por inicio e id; el único acceso es por un recorrido. El corte se fija una vez ({@code ahora} y su
 * hora de Lima), así el estado que se muestre coincide con el grupo.
 */
public final class HistorialReservas {

    private static final Comparator<ReservaConHorario> POR_INICIO_E_ID = Comparator
            .comparing(ReservaConHorario::inicio)
            .thenComparing(reserva -> reserva.reserva().getId(), Comparator.nullsFirst(Comparator.naturalOrder()));

    private final List<ReservaConHorario> ordenadas;
    private final Instant ahora;
    private final LocalDateTime ahoraLocal;

    public HistorialReservas(Collection<ReservaConHorario> reservas, Instant ahora, LocalDateTime ahoraLocal) {
        this.ordenadas = reservas.stream().sorted(POR_INICIO_E_ID).toList();
        this.ahora = Objects.requireNonNull(ahora, "ahora");
        this.ahoraLocal = Objects.requireNonNull(ahoraLocal, "ahoraLocal");
    }

    /** Ascendente por fecha, hora de inicio e id (R24). */
    public Iterator<ReservaConHorario> proximas() {
        return new IteradorReservas(ordenadas, true, esProxima());
    }

    /** Descendente por fecha, hora de inicio e id (R25). */
    public Iterator<ReservaConHorario> pasadas() {
        return new IteradorReservas(ordenadas, false, esProxima().negate());
    }

    public Iterator<ReservaConHorario> recorrer(GrupoReservas grupo) {
        return switch (grupo) {
            case PROXIMAS -> proximas();
            case PASADAS -> pasadas();
        };
    }

    private Predicate<ReservaConHorario> esProxima() {
        return reserva -> reserva.esProxima(ahora, ahoraLocal);
    }
}
