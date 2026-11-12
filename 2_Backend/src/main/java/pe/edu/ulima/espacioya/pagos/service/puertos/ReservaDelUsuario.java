package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.time.LocalDate;

/**
 * Una reserva propia vista por el historial de pagos (HU-28). {@code horaFin} va de 1 a 24 (fin del último bloque).
 * {@code espacio} es el nombre del espacio, aunque ya no se vea en el catálogo.
 */
public record ReservaDelUsuario(Long reservaId, Long espacioId, String espacio, LocalDate fecha, int horaInicio,
                                int horaFin) {
}
