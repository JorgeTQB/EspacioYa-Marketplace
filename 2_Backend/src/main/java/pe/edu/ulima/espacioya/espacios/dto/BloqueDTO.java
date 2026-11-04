package pe.edu.ulima.espacioya.espacios.dto;

import pe.edu.ulima.espacioya.espacios.domain.Bloque;

/** Un bloque de 1 hora; {@code estado} es el nombre de {@code EstadoBloque}. */
public record BloqueDTO(int horaInicio, int horaFin, String estado) {

    public static BloqueDTO de(Bloque bloque) {
        return new BloqueDTO(bloque.horaInicio(), bloque.horaFin(), bloque.estado().name());
    }
}
