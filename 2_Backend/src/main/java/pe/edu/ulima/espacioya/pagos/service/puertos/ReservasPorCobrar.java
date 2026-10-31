package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.time.Instant;
import java.util.Optional;

/** Lo que {@code pagos} necesita de una reserva. Lo define {@code pagos} y lo implementa {@code reservas} (DIP). */
public interface ReservasPorCobrar {

    /** La reserva si es de ese usuario, o vacío. */
    Optional<ReservaPorCobrar> deUsuario(Long reservaId, Long usuarioId);

    /**
     * Bloquea el espacio y luego la reserva hasta el fin de la transacción del llamador, y evalúa la vigencia después
     * de los dos bloqueos (HU-13 R36). Respeta el orden {@code usuario → espacio → reserva}. Vacío si la reserva no
     * existe.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay una transacción activa.
     */
    Optional<ReservaBloqueada> bloquear(Long reservaId);

    /**
     * Pasa la reserva a {@code CONFIRMADA} y lo audita. Sus bloques siguen activos.
     *
     * @param evaluadaEn el instante con el que {@link #bloquear} la declaró vigente.
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay una transacción activa.
     * @throws IllegalStateException si la reserva no es vigente en ese instante.
     */
    void confirmar(Long reservaId, Instant evaluadaEn);
}
