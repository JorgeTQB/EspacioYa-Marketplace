package pe.edu.ulima.espacioya.reservas.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;

/**
 * Implementación JPA de {@link ReservaDAO} (★ HU-14): combina {@link ReservaRepository} y
 * {@link ReservaBloqueRepository} con dos consultas, sin importar cuántas reservas haya (R35).
 */
@Repository
class ReservaDAOJpa implements ReservaDAO {

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;

    ReservaDAOJpa(ReservaRepository reservas, ReservaBloqueRepository bloques) {
        this.reservas = reservas;
        this.bloques = bloques;
    }

    @Override
    public List<ReservaConHorario> delUsuario(Long usuarioId) {
        return conHorario(reservas.findByUsuarioId(usuarioId));
    }

    @Override
    public List<ReservaConHorario> confirmadasDeEspacios(Collection<Long> espacioIds) {
        if (espacioIds.isEmpty()) {
            return List.of();
        }
        return conHorario(reservas.findByEspacioIdInAndEstado(espacioIds, EstadoReserva.CONFIRMADA));
    }

    // Una reserva sin filas de reserva_bloque no ocurre por la aplicación; si apareciera, se omite.
    private List<ReservaConHorario> conHorario(List<Reserva> encontradas) {
        if (encontradas.isEmpty()) {
            return List.of();
        }
        Map<Long, HorarioReserva> horarios = bloques.horarios(encontradas.stream().map(Reserva::getId).toList())
                .stream()
                .collect(Collectors.toMap(HorarioReserva::reservaId, Function.identity()));
        return encontradas.stream()
                .filter(reserva -> horarios.containsKey(reserva.getId()))
                .map(reserva -> {
                    var horario = horarios.get(reserva.getId());
                    return new ReservaConHorario(reserva, horario.fecha(), horario.horaInicio(),
                            horario.horaUltima() + 1);
                })
                .toList();
    }
}
