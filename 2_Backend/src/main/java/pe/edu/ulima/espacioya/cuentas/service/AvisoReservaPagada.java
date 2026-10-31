package pe.edu.ulima.espacioya.cuentas.service;

import java.time.LocalDate;

/** Contenido del aviso al arrendador (HU-13 D5): sin datos del usuario, sin monto y sin datos del pago (RNF-SEG-16). */
public record AvisoReservaPagada(String espacio, LocalDate fecha, int horaInicio, int horaFin) {
}
