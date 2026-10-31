package pe.edu.ulima.espacioya.reservas.domain;

/** Quién cancela una reserva (HU-15 D1). Lo fija la ruta, nunca el cuerpo de la solicitud (R7). */
public enum QuienCancela {
    USUARIO,
    ARRENDADOR;

    /** "USUARIO" o "ARRENDADOR": el rol que exige cada ruta (R2, R3). */
    public String rolRequerido() {
        return name();
    }
}
