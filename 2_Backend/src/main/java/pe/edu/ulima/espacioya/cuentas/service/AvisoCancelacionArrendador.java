package pe.edu.ulima.espacioya.cuentas.service;

import java.time.LocalDate;

/** Aviso de cancelación al arrendador (HU-15 D8): sin datos del usuario ni montos (RNF-SEG-16). */
public record AvisoCancelacionArrendador(String espacio, LocalDate fecha, int horaInicio, int horaFin,
                                         String canceladaPor) {
}
