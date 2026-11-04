package pe.edu.ulima.espacioya.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Entrada de {@code POST /api/admin/liquidaciones/{id}/pago} (HU-19 CA2). Solo el número de operación de la
 * transferencia: montos, destino, fecha y quién paga los pone el servidor (S8, STRIDE T). El cliente quita los
 * espacios extremos antes de enviarlo.
 */
public record MarcarLiquidacionPagadaDTO(
        @NotNull(message = MENSAJE_NUMERO_OPERACION)
        @Pattern(regexp = "[A-Za-z0-9-]{4,30}", message = MENSAJE_NUMERO_OPERACION)
        String numeroOperacion) {

    public static final String MENSAJE_NUMERO_OPERACION =
            "Ingresa el número de operación: de 4 a 30 letras, dígitos o guiones.";
}
