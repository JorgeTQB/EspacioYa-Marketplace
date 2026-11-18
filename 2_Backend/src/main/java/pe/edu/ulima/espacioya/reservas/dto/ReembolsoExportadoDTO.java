package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;

import pe.edu.ulima.espacioya.pagos.service.ReembolsoDePago;

/** Un reembolso de un pago exportado (HU-22 R6). */
public record ReembolsoExportadoDTO(BigDecimal monto, String estado, Instant creadoEn) {

    public static ReembolsoExportadoDTO de(ReembolsoDePago reembolso) {
        return new ReembolsoExportadoDTO(reembolso.monto(), reembolso.estado(), reembolso.creadoEn());
    }
}
