package pe.edu.ulima.espacioya.espacios.service.puertos;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un bloque de 1 hora retenido por una reserva activa; {@code pagado} si la reserva está CONFIRMADA. */
public record BloqueReservado(LocalDate fecha, int horaInicio, boolean pagado) {

    /**
     * El bloque termina (fecha + horaInicio + 1 h, hora de Lima) después de {@code ahoraLocal}: aún no terminó, sea
     * futuro o en curso (HU-21 DT-5). El puerto filtra por fecha, así que también trae bloques de hoy ya terminados.
     */
    public boolean terminaDespuesDe(LocalDateTime ahoraLocal) {
        return fecha.atTime(horaInicio, 0).plusHours(1).isAfter(ahoraLocal);
    }
}
