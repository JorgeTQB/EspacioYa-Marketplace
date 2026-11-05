package pe.edu.ulima.espacioya.reservas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import pe.edu.ulima.espacioya.pagos.service.PagoDeReserva;

/** Un pago de una reserva del usuario en la exportación (HU-22 R6), sin datos de la tarjeta ni de la pasarela (R13). */
public record PagoExportadoDTO(Long reservaId, BigDecimal monto, String estado, Instant creadoEn,
                               List<ReembolsoExportadoDTO> reembolsos) {

    public static PagoExportadoDTO de(PagoDeReserva pago) {
        return new PagoExportadoDTO(pago.reservaId(), pago.monto(), pago.estado(), pago.creadoEn(),
                pago.reembolsos().stream().map(ReembolsoExportadoDTO::de).toList());
    }
}
