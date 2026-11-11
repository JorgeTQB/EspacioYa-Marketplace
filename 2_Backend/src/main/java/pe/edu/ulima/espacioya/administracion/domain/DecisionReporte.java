package pe.edu.ulima.espacioya.administracion.domain;

/**
 * Decisión del admin sobre un reporte (HU-27). {@code OCULTAR_POR_FRAUDE} solo vale para un espacio y, además de
 * ocultarlo, cancela sus reservas por iniciar (P17, DT-17).
 */
public enum DecisionReporte {
    MANTENER,
    OCULTAR,
    OCULTAR_POR_FRAUDE;

    public boolean oculta() {
        return this != MANTENER;
    }

    public boolean esFraude() {
        return this == OCULTAR_POR_FRAUDE;
    }
}
