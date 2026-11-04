package pe.edu.ulima.espacioya.pagos.domain;

/** Por qué una liquidación no se puede marcar como pagada, en este orden de evaluación (HU-19 § 8). */
public enum MotivoNoPagable {
    YA_PAGADA,
    SIN_CUENTA,
    CUENTA_RETENIDA
}
