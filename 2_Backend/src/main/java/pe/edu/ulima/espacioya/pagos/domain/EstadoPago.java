package pe.edu.ulima.espacioya.pagos.domain;

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
    REEMBOLSO_FALLIDO
}
