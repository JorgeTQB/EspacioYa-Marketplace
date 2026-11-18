package pe.edu.ulima.espacioya.pagos.service.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo único que el generador del comprobante conoce del pago (HU-28 R12 a R16). Sin id de la pasarela, correo, teléfono
 * ni nombre: no hay por dónde filtrarlos. {@code ultimos4} puede venir nulo o inválido; el generador lo valida.
 * {@code montoReembolsado} es nulo si el pago no está reembolsado.
 */
public record DatosComprobante(Long numero, LocalDate fechaPago, String espacio, LocalDate fechaReserva,
                               int horaInicio, int horaFin, BigDecimal monto, String estado,
                               BigDecimal montoReembolsado, String ultimos4, boolean simulado) {
}
