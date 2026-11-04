package pe.edu.ulima.espacioya.espacios.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;

/**
 * Horario semanal completo de un espacio (HU-08). Al guardarlo reemplaza al anterior; una lista vacía deja el
 * espacio sin horario. Sin superposición caben como mucho 24 franjas por día × 7 días = 168.
 */
public record HorarioDTO(
        @NotNull(message = HorarioDTO.M_FRANJAS)
        @Size(max = HorarioDTO.MAXIMO_FRANJAS, message = HorarioDTO.M_FRANJAS)
        List<@NotNull(message = FranjaDTO.M_FRANJA) @Valid FranjaDTO> franjas) {

    public static final int MAXIMO_FRANJAS = 168;
    public static final String M_FRANJAS = "Envía la lista de franjas del horario (como mucho 168).";

    /** En el orden recibido: los índices de {@code errores[]} se refieren a él. */
    public List<FranjaHoraria> aFranjas() {
        return franjas.stream().map(FranjaDTO::aFranja).toList();
    }

    /** Salida ordenada por día y luego por hora de inicio. */
    public static HorarioDTO de(List<FranjaHoraria> franjas) {
        return new HorarioDTO(franjas.stream().sorted().map(FranjaDTO::de).toList());
    }
}
