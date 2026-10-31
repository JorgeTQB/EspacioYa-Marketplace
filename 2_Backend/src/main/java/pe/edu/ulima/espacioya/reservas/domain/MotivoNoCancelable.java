package pe.edu.ulima.espacioya.reservas.domain;

/** Por qué una reserva no se puede cancelar (HU-15 D2, R8–R10), en el orden en que se evalúa. */
public enum MotivoNoCancelable {
    YA_CANCELADA,
    NO_CONFIRMADA,
    YA_INICIADA
}
