package pe.edu.ulima.espacioya.administracion.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import pe.edu.ulima.espacioya.pagos.domain.ReglasLiquidacion;

/**
 * Regla pura de la ventana de reclamo (HU-26 DT-5, P2): {@code inicio ≤ ahora < fin + PLAZO}. El plazo es la misma
 * espera de la liquidación (HU-19): el borde superior coincide con el inferior de {@code esLiquidable}, sin hueco ni
 * solape.
 */
public final class ReglasReclamo {

    public static final Duration PLAZO = ReglasLiquidacion.ESPERA;
    public static final int MAXIMO_MENSAJES_POR_PARTE = 10;

    private ReglasReclamo() {
    }

    /** Vacío si se puede reclamar. No confirmada o sin empezar: NO_RECLAMABLE; ahora ≥ fin + PLAZO: PLAZO_VENCIDO. */
    public static Optional<MotivoNoReclamable> motivoNoReclamable(boolean confirmada, Instant inicio, Instant fin,
                                                                  Instant ahora) {
        if (!confirmada || ahora.isBefore(inicio)) {
            return Optional.of(MotivoNoReclamable.NO_RECLAMABLE);
        }
        if (!ahora.isBefore(fin.plus(PLAZO))) {
            return Optional.of(MotivoNoReclamable.PLAZO_VENCIDO);
        }
        return Optional.empty();
    }
}
