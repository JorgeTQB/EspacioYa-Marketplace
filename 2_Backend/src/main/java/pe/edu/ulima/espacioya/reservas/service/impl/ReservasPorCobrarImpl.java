package pe.edu.ulima.espacioya.reservas.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaBloqueada;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

/**
 * Reservas para {@code pagos} (HU-13). {@link #bloquear} sigue el orden {@code espacio → reserva} de HU-12, sin el
 * bloqueo de {@code usuario}: solo protege el cupo y confirmar lo reduce (DT-6). Compite con la expiración por la fila.
 */
@Service
public class ReservasPorCobrarImpl implements ReservasPorCobrar {

    public static final String ACCION_RESERVA_CONFIRMADA = "RESERVA_CONFIRMADA";

    private static final String ENTIDAD_RESERVA = "reserva";

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final DisponibilidadService disponibilidad;
    private final CatalogoEspaciosService catalogo;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public ReservasPorCobrarImpl(ReservaRepository reservas, ReservaBloqueRepository bloques,
                                 DisponibilidadService disponibilidad, CatalogoEspaciosService catalogo,
                                 AuditoriaService auditoria, Clock reloj) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.disponibilidad = disponibilidad;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservaPorCobrar> deUsuario(Long reservaId, Long usuarioId) {
        var ahora = Instant.now(reloj);
        return reservas.findByIdAndUsuarioId(reservaId, usuarioId)
                .map(reserva -> new ReservaPorCobrar(reserva.getId(), reserva.getMontoTotal(), reserva.getExpiraEn(),
                        reserva.esVigente(ahora), catalogo.esVisible(reserva.getEspacioId())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReservaBloqueada> bloquear(Long reservaId) {
        var espacioId = reservas.findEspacioIdById(reservaId);
        if (espacioId.isEmpty()) {
            return Optional.empty();
        }
        var espacio = disponibilidad.bloquearEspacio(espacioId.get());
        var reserva = reservas.findParaActualizar(reservaId)
                .orElseThrow(() -> new IllegalStateException("La reserva no puede desaparecer: no se borran"));
        var filas = bloques.findByReservaIdOrderByHoraInicioAsc(reservaId);
        // Después de los bloqueos: el proceso de expiración compite por esta fila.
        var ahora = Instant.now(reloj);
        return Optional.of(new ReservaBloqueada(reserva.getId(), reserva.getUsuarioId(), espacio.arrendadorId(),
                espacio.nombre(), filas.getFirst().getFecha(), filas.getFirst().getHoraInicio(),
                filas.getLast().getHoraInicio() + 1, reserva.getMontoTotal(), reserva.esVigente(ahora), ahora));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmar(Long reservaId, Instant evaluadaEn) {
        var reserva = reservas.findParaActualizar(reservaId)
                .orElseThrow(() -> new IllegalStateException("La reserva no puede desaparecer: no se borran"));
        reserva.confirmar(evaluadaEn);
        auditoria.registrar(new EventoAuditoria(null, ACCION_RESERVA_CONFIRMADA, ENTIDAD_RESERVA, reserva.getId(), null,
                "origen=WEBHOOK;espacioId=" + reserva.getEspacioId()));
    }
}
