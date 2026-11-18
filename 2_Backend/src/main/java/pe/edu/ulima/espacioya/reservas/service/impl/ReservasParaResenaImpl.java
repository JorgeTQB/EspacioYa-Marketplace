package pe.edu.ulima.espacioya.reservas.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaResena;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/**
 * Lee siempre con {@link ReservaDAO#delUsuario}, también para buscar por id: el DAO exige el dueño, así que una reserva
 * ajena simplemente no aparece (DT-3, STRIDE E/I). Filtrar en memoria es el riesgo que HU-14 ya aceptó.
 */
@Service
@Transactional(readOnly = true)
public class ReservasParaResenaImpl implements ReservasParaResena {

    private final ReservaDAO reservas;
    private final Clock reloj;

    public ReservasParaResenaImpl(ReservaDAO reservas, Clock reloj) {
        this.reservas = reservas;
        this.reloj = reloj;
    }

    @Override
    public ReservaParaResena delUsuario(Long usuarioId, Long reservaId) {
        var ahora = Instant.now(reloj);
        return reservas.delUsuario(usuarioId).stream()
                .filter(conHorario -> conHorario.reserva().getId().equals(reservaId))
                .findFirst()
                .map(conHorario -> paraResena(conHorario, ahora))
                .orElseThrow(RecursoNoEncontradoException::new);
    }

    @Override
    public List<ReservaParaResena> confirmadasDe(Long usuarioId) {
        var ahora = Instant.now(reloj);
        return reservas.delUsuario(usuarioId).stream()
                .map(conHorario -> paraResena(conHorario, ahora))
                .filter(ReservaParaResena::confirmada)
                .toList();
    }

    private static ReservaParaResena paraResena(ReservaConHorario conHorario, Instant ahora) {
        var reserva = conHorario.reserva();
        return new ReservaParaResena(reserva.getId(), reserva.getEspacioId(),
                reserva.estadoEn(ahora) == EstadoReserva.CONFIRMADA, conHorario.instanteDeFin());
    }
}
