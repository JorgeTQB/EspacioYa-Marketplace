package pe.edu.ulima.espacioya.cuentas.dto;

import java.time.Instant;

/** Consentimiento de HU-01 tal como lo guarda {@code usuario}; la IP solo vive en la auditoría y no se exporta. */
public record ConsentimientoDTO(String versionPolitica, Instant fechaConsentimiento) {
}
