package pe.edu.ulima.espacioya.pagos.service.reembolso;

/** Resultado de ejecutar un comando de reembolso. {@code NO_EJECUTADO}: ya reembolsado o en proceso (R21, R22). */
public enum ResultadoReembolso {
    REEMBOLSADO,
    FALLIDO,
    NO_EJECUTADO
}
