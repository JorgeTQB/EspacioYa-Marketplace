package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import java.time.LocalDate;

/**
 * Evento de los recordatorios (HU-25 R29): solo el nombre del espacio, la fecha y el horario. Sin montos, datos de
 * pago, documentos ni datos de contacto de la otra parte. {@code horaFin} va de 1 a 24.
 */
public record DatosRecordatorio(String espacio, LocalDate fecha, int horaInicio, int horaFin) {
}
