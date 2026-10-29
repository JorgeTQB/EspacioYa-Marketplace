package pe.edu.ulima.espacioya.cuentas.dto;

import java.time.Instant;

/**
 * Un arrendador de la cola de revisión de identidad (HU-06, R20). Solo lo necesario para elegir a quién revisar:
 * el expediente completo se pide aparte ({@link ExpedienteIdentidadDTO}).
 */
public record ArrendadorEnRevisionDTO(Long arrendadorId, String nombre, String razonSocial, String tipoDocumento,
                                      Instant enviadoEn) {
}
