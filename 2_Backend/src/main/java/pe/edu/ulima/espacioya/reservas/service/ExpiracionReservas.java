package pe.edu.ulima.espacioya.reservas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

/**
 * Pasa a {@code EXPIRADA} las reservas vencidas y libera sus bloques (HU-12 D2, capas 2 y 3). Nunca toma el bloqueo de
 * {@code espacio} desde el proceso programado: solo una fila de {@code reserva} por transacción, para no cruzarse con
 * el orden {@code usuario → espacio → reserva} (DT-1).
 */
@Service
public class ExpiracionReservas {

    public static final String ACCION_RESERVA_EXPIRADA = "RESERVA_EXPIRADA";

    private static final String ENTIDAD_RESERVA = "reserva";
    private static final Logger log = LoggerFactory.getLogger(ExpiracionReservas.class);

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public ExpiracionReservas(ReservaRepository reservas, ReservaBloqueRepository bloques, AuditoriaService auditoria,
                              TransactionTemplate transacciones, Clock reloj) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    /** Una transacción por reserva: un fallo no deshace las ya expiradas ni detiene las demás (R15). */
    public int expirarVencidas() {
        var ahora = Instant.now(reloj);
        var expiradas = 0;
        for (var id : reservas.findIdsVencidas(ahora)) {
            try {
                if (Boolean.TRUE.equals(transacciones.execute(estado -> expirarSiSigueVencida(id, ahora)))) {
                    expiradas++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo expirar la reserva {}", id);
            }
        }
        return expiradas;
    }

    /** Expira las vencidas que retienen estas horas. El llamador ya tiene el bloqueo del espacio (R17). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberarVencidas(Long espacioId, LocalDate fecha, List<Integer> horas, Instant ahora) {
        var horasInicio = horas.stream().map(Integer::shortValue).toList();
        reservas.findVencidasQueRetienen(espacioId, fecha, horasInicio, ahora)
                .forEach(reserva -> expirar(reserva, ahora));
    }

    // Otro proceso pudo confirmarla o expirarla entre la lectura de ids y el bloqueo (R16).
    private boolean expirarSiSigueVencida(Long id, Instant ahora) {
        var reserva = reservas.findParaActualizar(id).filter(encontrada -> encontrada.estaVencida(ahora));
        reserva.ifPresent(vencida -> expirar(vencida, ahora));
        return reserva.isPresent();
    }

    private void expirar(Reserva reserva, Instant ahora) {
        reserva.expirar(ahora);
        bloques.desactivarDeReserva(reserva.getId());
        auditoria.registrar(new EventoAuditoria(null, ACCION_RESERVA_EXPIRADA, ENTIDAD_RESERVA, reserva.getId(), null,
                "origen=PROCESO;espacioId=" + reserva.getEspacioId()));
    }
}
