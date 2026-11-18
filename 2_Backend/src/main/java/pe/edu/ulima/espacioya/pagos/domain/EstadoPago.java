package pe.edu.ulima.espacioya.pagos.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Resultado de cada intento en la pasarela (HU-13). {@code PAGADO_TARDIO} y {@code MONTO_DISTINTO} son pagos aprobados
 * que no confirmaron la reserva: su devolución es manual (D2, S3). HU-19 y HU-28 deben contar solo {@code PAGADO}.
 */
public enum EstadoPago {
    PAGADO,
    RECHAZADO,
    PAGADO_TARDIO,
    MONTO_DISTINTO,
    REEMBOLSADO,
    REEMBOLSO_FALLIDO;

    /** Los estados que ve el usuario en "Mis pagos" y de los que descarga comprobante (HU-28 R2, R3, RN-07). */
    public static final Set<EstadoPago> EN_HISTORIAL = EnumSet.of(PAGADO, REEMBOLSADO);
}
