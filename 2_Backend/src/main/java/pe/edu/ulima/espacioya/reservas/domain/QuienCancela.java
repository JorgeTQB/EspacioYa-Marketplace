package pe.edu.ulima.espacioya.reservas.domain;

/**
 * Quién cancela una reserva (HU-15 D1). Lo fija la ruta, nunca el cuerpo de la solicitud (R7). {@code ADMIN} solo
 * cancela al ocultar un espacio por fraude (HU-27 DT-19): las rutas de HU-15 no lo aceptan.
 */
public enum QuienCancela {
    USUARIO,
    ARRENDADOR,
    ADMIN;

    /** "USUARIO", "ARRENDADOR" o "ADMIN": el rol que exige cada caso (R2, R3). */
    public String rolRequerido() {
        return name();
    }
}
