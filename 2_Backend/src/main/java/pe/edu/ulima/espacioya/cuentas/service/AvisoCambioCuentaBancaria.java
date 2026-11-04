package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Instant;

/**
 * Contenido del aviso de cambio de cuenta bancaria (HU-18 DT-10): banco, los 4 últimos dígitos y hasta cuándo queda
 * retenida la liquidación. Nunca el número completo (R22).
 */
public record AvisoCambioCuentaBancaria(String banco, String numeroEnmascarado, Instant retenidaHasta) {
}
