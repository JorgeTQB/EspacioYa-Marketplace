package pe.edu.ulima.espacioya.administracion.dto;

import java.util.List;

/** Ids de las reservas propias que admiten un reclamo, ascendentes (HU-26 R33). */
public record ReclamablesDTO(List<Long> reservaIds) {
}
