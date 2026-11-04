package pe.edu.ulima.espacioya.pagos.domain;

import java.util.Set;

/** Estado de una liquidación (HU-19): {@code PENDIENTE_CUENTA} o {@code GENERADA} → {@code PAGADA}, que es final. */
public enum EstadoLiquidacion {
    PENDIENTE_CUENTA,
    GENERADA,
    PAGADA;

    /** "Por pagar": las que el admin aún debe transferir (S9, S13). */
    public static final Set<EstadoLiquidacion> POR_PAGAR = Set.of(PENDIENTE_CUENTA, GENERADA);
}
