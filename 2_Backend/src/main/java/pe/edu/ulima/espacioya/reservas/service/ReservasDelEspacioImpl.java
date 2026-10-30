package pe.edu.ulima.espacioya.reservas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.repository.BloqueActivo;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;

/**
 * Bloques que retienen las reservas activas (HU-12): {@code PENDIENTE_PAGO} vigente o {@code CONFIRMADA}. Una vencida
 * deja de ocupar aunque el proceso de expiración aún no la haya procesado (HU-12 D2, capa 1).
 */
@Service
public class ReservasDelEspacioImpl implements ReservasDelEspacio {

    private final ReservaBloqueRepository bloques;
    private final Clock reloj;

    public ReservasDelEspacioImpl(ReservaBloqueRepository bloques, Clock reloj) {
        this.bloques = bloques;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloqueReservado> bloquesReservados(Long espacioId, LocalDate desde, LocalDate hasta) {
        return bloques.bloquesActivos(espacioId, desde, hasta, Instant.now(reloj)).stream()
                .map(ReservasDelEspacioImpl::aBloqueReservado)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<BloqueReservado>> bloquesReservadosDelDia(Collection<Long> espacioIds, LocalDate fecha) {
        if (espacioIds.isEmpty()) {
            return Map.of();
        }
        return bloques.bloquesActivosDelDia(espacioIds, fecha, Instant.now(reloj)).stream()
                .collect(Collectors.groupingBy(BloqueActivo::espacioId,
                        Collectors.mapping(ReservasDelEspacioImpl::aBloqueReservado, Collectors.toList())));
    }

    private static BloqueReservado aBloqueReservado(BloqueActivo bloque) {
        return new BloqueReservado(bloque.fecha(), bloque.horaInicio(), bloque.estado() == EstadoReserva.CONFIRMADA);
    }
}
