package pe.edu.ulima.espacioya.espacios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Fecha que se bloquea (HU-08). Llega como texto para que un {@code "2026-13-01"} se reporte en el campo
 * {@code fecha} y no como un cuerpo ilegible sin campo (DT-14); el servicio comprueba el calendario.
 */
public record BloqueoFechaDTO(
        @NotBlank(message = BloqueoFechaDTO.M_FECHA)
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = BloqueoFechaDTO.M_FECHA)
        String fecha) {

    public static final String M_FECHA = "Elige una fecha válida.";
}
