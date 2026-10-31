package pe.edu.ulima.espacioya.reservas.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;

/**
 * Una sola fuente del {@link ReservaDTO} (HU-14 DT-11): HU-12 y "Mis reservas" lo arman igual. También la única regla
 * del id de las rutas {@code /{id}} de reservas (HU-15 DT-10).
 */
public final class ConversionReservas {

    private static final Pattern ID_BIEN_ESCRITO = Pattern.compile("[0-9]{1,19}");

    private ConversionReservas() {
    }

    /** El estado se lee con {@code estadoEn(ahora)} y los segundos con el mismo {@code ahora} (HU-12 D2, R40). */
    public static ReservaDTO aDto(Reserva reserva, String espacioNombre, LocalDate fecha, int horaInicio, int horaFin,
                           Instant ahora) {
        return new ReservaDTO(reserva.getId(), reserva.getEspacioId(), espacioNombre, reserva.estadoEn(ahora).name(),
                fecha, horaInicio, horaFin, reserva.getPrecioHoraAplicado(), reserva.getMontoTotal(),
                reserva.getExpiraEn(), reserva.segundosRestantes(ahora));
    }

    /** 1 a 19 dígitos, sin desbordar un {@code long} y mayor que 0; si no, vacío (HU-12 DT-9). */
    public static Optional<Long> idReserva(String texto) {
        if (texto == null || !ID_BIEN_ESCRITO.matcher(texto).matches()) {
            return Optional.empty();
        }
        try {
            var id = Long.parseLong(texto);
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
