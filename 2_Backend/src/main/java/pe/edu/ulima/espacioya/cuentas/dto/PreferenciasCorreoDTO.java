package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotNull;

// Sin id: la cuenta sale de la sesión y Jackson ignora los campos extra, como usuarioId (HU-25 R3).
public record PreferenciasCorreoDTO(
        @NotNull(message = PreferenciasCorreoDTO.MENSAJE_RECORDATORIOS_OBLIGATORIO)
        Boolean recordatoriosActivos) {

    public static final String MENSAJE_RECORDATORIOS_OBLIGATORIO = "Indica si quieres recibir recordatorios.";
}
