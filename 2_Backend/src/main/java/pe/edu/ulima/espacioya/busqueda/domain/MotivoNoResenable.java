package pe.edu.ulima.espacioya.busqueda.domain;

public enum MotivoNoResenable {
    /** La reserva no está confirmada o aún no termina. */
    NO_USADA,
    /** Pasaron los 14 días desde su fin. */
    PLAZO_VENCIDO
}
