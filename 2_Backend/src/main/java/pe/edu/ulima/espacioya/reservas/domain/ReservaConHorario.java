package pe.edu.ulima.espacioya.reservas.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;

/**
 * Una reserva con su horario (HU-14, TransferObject del DAO): {@code fecha} y horas salen de todas sus filas de
 * {@code reserva_bloque}, activas o no. {@code horaFin} va de 1 a 24.
 */
public record ReservaConHorario(Reserva reserva, LocalDate fecha, int horaInicio, int horaFin) {

    public ReservaConHorario {
        Objects.requireNonNull(reserva, "reserva");
        Objects.requireNonNull(fecha, "fecha");
    }

    public LocalDateTime inicio() {
        return fecha.atTime(horaInicio, 0);
    }

    /** {@link #inicio()} en America/Lima como instante (HU-15 R14). */
    public Instant instanteDeInicio() {
        return inicio().atZone(ZonaHoraria.LIMA).toInstant();
    }

    /** Con {@code horaFin = 24}, las 00:00 del día siguiente. */
    public LocalDateTime fin() {
        return fecha.atStartOfDay().plusHours(horaFin);
    }

    /**
     * Estado leído {@code PENDIENTE_PAGO} (vigente) o {@code CONFIRMADA} y fin posterior a {@code ahoraLocal} (D2, R22,
     * R23). Una vencida se lee {@code EXPIRADA} con {@code estadoEn}, así que va a pasadas aunque su fila siga pendiente.
     */
    public boolean esProxima(Instant ahora, LocalDateTime ahoraLocal) {
        var estado = reserva.estadoEn(ahora);
        return (estado == EstadoReserva.PENDIENTE_PAGO || estado == EstadoReserva.CONFIRMADA)
                && fin().isAfter(ahoraLocal);
    }
}
