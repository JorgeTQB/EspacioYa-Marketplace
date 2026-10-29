package pe.edu.ulima.espacioya.espacios.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.annotation.JsonDeserialize;

import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;

/**
 * Franja del horario semanal (HU-08): día ISO 1–7 y horas enteras con fin exclusivo. Solo se aceptan números JSON
 * enteros: un {@code 8.5}, un {@code "8"} o un booleano se leen como {@code null} y fallan con el error del campo.
 */
public record FranjaDTO(
        @NotNull(message = FranjaDTO.M_DIA)
        @Min(value = 1, message = FranjaDTO.M_DIA)
        @Max(value = 7, message = FranjaDTO.M_DIA)
        @JsonDeserialize(using = FichaEspacioDTO.SoloEntero.class)
        Integer diaSemana,

        @NotNull(message = FranjaDTO.M_HORA_INICIO)
        @Min(value = 0, message = FranjaDTO.M_HORA_INICIO)
        @Max(value = 23, message = FranjaDTO.M_HORA_INICIO)
        @JsonDeserialize(using = FichaEspacioDTO.SoloEntero.class)
        Integer horaInicio,

        @NotNull(message = FranjaDTO.M_HORA_FIN)
        @Min(value = 1, message = FranjaDTO.M_HORA_FIN)
        @Max(value = 24, message = FranjaDTO.M_HORA_FIN)
        @JsonDeserialize(using = FichaEspacioDTO.SoloEntero.class)
        Integer horaFin) {

    public static final String M_FRANJA = "Completa la franja.";
    public static final String M_DIA = "Elige un día de la semana.";
    public static final String M_HORA_INICIO = "La hora de inicio debe ser una hora en punto entre 00:00 y 23:00.";
    public static final String M_HORA_FIN = "La hora de fin debe ser una hora en punto entre 01:00 y 24:00.";

    public static FranjaDTO de(FranjaHoraria franja) {
        return new FranjaDTO(franja.diaSemana(), franja.horaInicio(), franja.horaFin());
    }

    public FranjaHoraria aFranja() {
        return new FranjaHoraria(diaSemana, horaInicio, horaFin);
    }
}
