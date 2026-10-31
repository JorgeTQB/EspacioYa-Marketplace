package pe.edu.ulima.espacioya.reservas.domain;

import java.util.Optional;

/** Grupo de "Mis reservas" y "Reservas de mis espacios" (HU-14 D2): el servidor decide el corte. */
public enum GrupoReservas {
    PROXIMAS("proximas"),
    PASADAS("pasadas");

    private final String texto;

    GrupoReservas(String texto) {
        this.texto = texto;
    }

    /** Solo "proximas" o "pasadas" exactos (minúsculas, sin espacios); si no, vacío (R30). */
    public static Optional<GrupoReservas> desde(String texto) {
        for (var grupo : values()) {
            if (grupo.texto.equals(texto)) {
                return Optional.of(grupo);
            }
        }
        return Optional.empty();
    }
}
