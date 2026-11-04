package pe.edu.ulima.espacioya.cuentas.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contenido del aviso de cancelación al usuario (HU-15 D8): quién canceló, porcentaje y monto a reembolsar. */
public record AvisoCancelacionUsuario(String espacio, LocalDate fecha, int horaInicio, int horaFin,
                                      String canceladaPor, int porcentaje, BigDecimal montoReembolso) {
}
