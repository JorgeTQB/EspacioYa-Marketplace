package pe.edu.ulima.espacioya.pagos.dto;

import java.time.Instant;
import java.util.List;

/** Respuesta de la generación (HU-19 R19): el corte usado y las liquidaciones creadas, posiblemente ninguna (R18). */
public record GeneracionLiquidacionesDTO(Instant corte, List<LiquidacionAdminDTO> liquidaciones) {
}
