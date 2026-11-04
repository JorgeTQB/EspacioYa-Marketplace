package pe.edu.ulima.espacioya.reservas.service.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.HorarioReserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

/**
 * Reservas para la liquidación de {@code pagos} (HU-19 DT-4). No usa {@code ReservaDAO}: el DAO exige el dueño en
 * cada consulta (HU-14) y esta es una consulta del sistema con ids que salen de {@code pagos}. Una consulta por lote a
 * reservas, horarios, nombres y arrendadores: sin N+1. Solo lee.
 */
@Service
public class ReservasLiquidablesImpl implements ReservasLiquidables {

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final CatalogoEspaciosService catalogo;

    public ReservasLiquidablesImpl(ReservaRepository reservas, ReservaBloqueRepository bloques,
                                   CatalogoEspaciosService catalogo) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaLiquidable> describir(Collection<Long> reservaIds) {
        if (reservaIds.isEmpty()) {
            return List.of();
        }
        var encontradas = reservas.findAllById(Set.copyOf(reservaIds));
        if (encontradas.isEmpty()) {
            return List.of();
        }
        // Todas las filas de reserva_bloque, activas o no: una cancelada conserva su horario (HU-14 R8).
        Map<Long, HorarioReserva> horarios = bloques.horarios(encontradas.stream().map(Reserva::getId).toList())
                .stream()
                .collect(Collectors.toMap(HorarioReserva::reservaId, Function.identity()));
        var espacioIds = encontradas.stream().map(Reserva::getEspacioId).collect(Collectors.toSet());
        var nombres = catalogo.nombres(espacioIds);
        var arrendadores = catalogo.arrendadoresDe(espacioIds);
        return encontradas.stream()
                .filter(reserva -> horarios.containsKey(reserva.getId()))
                .filter(reserva -> arrendadores.containsKey(reserva.getEspacioId()))
                .sorted(Comparator.comparing(Reserva::getId))
                .map(reserva -> describir(reserva, horarios.get(reserva.getId()), nombres, arrendadores))
                .toList();
    }

    private static ReservaLiquidable describir(Reserva reserva, HorarioReserva horario, Map<Long, String> nombres,
                                               Map<Long, Long> arrendadores) {
        var conHorario = new ReservaConHorario(reserva, horario.fecha(), horario.horaInicio(),
                horario.horaUltima() + 1);
        return new ReservaLiquidable(reserva.getId(), arrendadores.get(reserva.getEspacioId()), reserva.getEspacioId(),
                nombres.get(reserva.getEspacioId()), conHorario.fecha(), conHorario.horaInicio(), conHorario.horaFin(),
                conHorario.instanteDeFin(), reserva.admiteLiquidacion(),
                reserva.getEstado() == EstadoReserva.CANCELADA);
    }
}
