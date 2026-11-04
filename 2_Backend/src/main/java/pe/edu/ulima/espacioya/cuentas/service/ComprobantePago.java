package pe.edu.ulima.espacioya.cuentas.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contenido del comprobante del usuario (HU-13 D5). De la tarjeta solo van los últimos 4, que pueden faltar. */
public record ComprobantePago(String espacio, LocalDate fecha, int horaInicio, int horaFin, BigDecimal monto,
                              String ultimos4, String referencia) {
}
