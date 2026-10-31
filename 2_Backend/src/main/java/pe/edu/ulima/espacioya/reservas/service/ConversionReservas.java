package pe.edu.ulima.espacioya.reservas.service;

import java.time.Instant;
import java.time.LocalDate;

import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;

/** Una sola fuente del {@link ReservaDTO} (HU-14 DT-11): HU-12 y "Mis reservas" lo arman igual. */
final class ConversionReservas {

    private ConversionReservas() {
    }

    /** El estado se lee con {@code estadoEn(ahora)} y los segundos con el mismo {@code ahora} (HU-12 D2, R40). */
    static ReservaDTO aDto(Reserva reserva, String espacioNombre, LocalDate fecha, int horaInicio, int horaFin,
                           Instant ahora) {
        return new ReservaDTO(reserva.getId(), reserva.getEspacioId(), espacioNombre, reserva.estadoEn(ahora).name(),
                fecha, horaInicio, horaFin, reserva.getPrecioHoraAplicado(), reserva.getMontoTotal(),
                reserva.getExpiraEn(), reserva.segundosRestantes(ahora));
    }
}
