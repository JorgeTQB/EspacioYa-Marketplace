package pe.edu.ulima.espacioya.reservas.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.HorarioReserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

/**
 * Reservas para los reclamos de {@code administracion} (HU-26 DT-2). Lo que pide el dueño se lee con
 * {@link ReservaDAO#delUsuario}, que exige el dueño: una reserva ajena simplemente no aparece (R9). {@link #describir}
 * es una consulta del sistema por lote, como {@code ReservasLiquidablesImpl}: el llamador ya validó las partes con la
 * fila de {@code reclamo}.
 */
@Service
@Transactional(readOnly = true)
public class ReservasParaReclamoImpl implements ReservasParaReclamo {

    private final ReservaDAO dao;
    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final CatalogoEspaciosService catalogo;
    private final Clock reloj;

    public ReservasParaReclamoImpl(ReservaDAO dao, ReservaRepository reservas, ReservaBloqueRepository bloques,
                                   CatalogoEspaciosService catalogo, Clock reloj) {
        this.dao = dao;
        this.reservas = reservas;
        this.bloques = bloques;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    @Override
    public ReservaParaReclamo delUsuario(Long usuarioId, Long reservaId) {
        var suya = dao.delUsuario(usuarioId).stream()
                .filter(conHorario -> conHorario.reserva().getId().equals(reservaId))
                .findFirst()
                .orElseThrow(RecursoNoEncontradoException::new);
        return paraReclamo(List.of(suya)).getFirst();
    }

    @Override
    public List<ReservaParaReclamo> confirmadasDe(Long usuarioId) {
        var ahora = Instant.now(reloj);
        var confirmadas = dao.delUsuario(usuarioId).stream()
                .filter(conHorario -> conHorario.reserva().estadoEn(ahora) == EstadoReserva.CONFIRMADA)
                .toList();
        return paraReclamo(confirmadas);
    }

    @Override
    public List<ReservaParaReclamo> describir(Collection<Long> reservaIds) {
        if (reservaIds.isEmpty()) {
            return List.of();
        }
        var encontradas = reservas.findAllById(Set.copyOf(reservaIds));
        if (encontradas.isEmpty()) {
            return List.of();
        }
        // Todas las filas de reserva_bloque, activas o no (HU-14 R8).
        Map<Long, HorarioReserva> horarios = bloques.horarios(encontradas.stream().map(Reserva::getId).toList())
                .stream()
                .collect(Collectors.toMap(HorarioReserva::reservaId, Function.identity()));
        var conHorario = encontradas.stream()
                .filter(reserva -> horarios.containsKey(reserva.getId()))
                .sorted(Comparator.comparing(Reserva::getId))
                .map(reserva -> {
                    var horario = horarios.get(reserva.getId());
                    return new ReservaConHorario(reserva, horario.fecha(), horario.horaInicio(),
                            horario.horaUltima() + 1);
                })
                .toList();
        return paraReclamo(conHorario);
    }

    // Una sola consulta de nombres y otra de arrendadores para todo el lote.
    private List<ReservaParaReclamo> paraReclamo(List<ReservaConHorario> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        var ahora = Instant.now(reloj);
        var espacioIds = lista.stream().map(conHorario -> conHorario.reserva().getEspacioId())
                .collect(Collectors.toSet());
        var nombres = catalogo.nombres(espacioIds);
        var arrendadores = catalogo.arrendadoresDe(espacioIds);
        return lista.stream().map(conHorario -> {
            var reserva = conHorario.reserva();
            return new ReservaParaReclamo(reserva.getId(), reserva.getUsuarioId(), reserva.getEspacioId(),
                    nombres.get(reserva.getEspacioId()), arrendadores.get(reserva.getEspacioId()),
                    reserva.estadoEn(ahora) == EstadoReserva.CONFIRMADA, conHorario.fecha(), conHorario.horaInicio(),
                    conHorario.horaFin(), conHorario.instanteDeInicio(), conHorario.instanteDeFin());
        }).toList();
    }
}
