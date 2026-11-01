package pe.edu.ulima.espacioya.pagos.domain;

/** Estado de un reembolso (HU-16 D1): {@code PENDIENTE} → {@code REEMBOLSADO} o {@code FALLIDO}. */
public enum EstadoReembolso {
    PENDIENTE,
    REEMBOLSADO,
    FALLIDO
}
