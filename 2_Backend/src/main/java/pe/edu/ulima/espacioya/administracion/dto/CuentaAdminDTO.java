package pe.edu.ulima.espacioya.administracion.dto;

import pe.edu.ulima.espacioya.cuentas.dto.DetalleCuentaDTO;

/**
 * Detalle de una cuenta en el panel admin (HU-09, R7, R10, R11): los datos los da {@code cuentas} y la suspensión
 * vigente, nula si la cuenta no está suspendida, sale del historial de {@code administracion}.
 */
public record CuentaAdminDTO(DetalleCuentaDTO cuenta, SuspensionVigenteDTO suspension) {
}
