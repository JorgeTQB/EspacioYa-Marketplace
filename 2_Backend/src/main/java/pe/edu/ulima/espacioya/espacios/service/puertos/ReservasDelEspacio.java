package pe.edu.ulima.espacioya.espacios.service.puertos;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto que implementa {@code reservas} (HU-12); {@code espacios} no depende de {@code reservas}. Mientras nadie lo
 * implemente, {@code DisponibilidadServiceImpl} trata todas las fechas como sin reservas (HU-08 DT-7, R21).
 *
 * <p>Contrato de concurrencia (HU-08 DT-10): al crear o confirmar una reserva, la implementación debe tomar el mismo
 * bloqueo del espacio que toma el bloqueo de fechas ({@code EspacioRepository.findParaActualizar} o un
 * {@code SELECT ... FOR UPDATE} sobre la fila de {@code espacio}) o releer {@code bloqueo_fecha} y el horario dentro
 * de su transacción tras bloquearlo, para no reservar en un día que se acaba de bloquear.
 */
public interface ReservasDelEspacio {

    /**
     * Bloques retenidos por reservas activas del espacio (PENDIENTE_PAGO vigente o CONFIRMADA) con fecha entre
     * desde y hasta, ambas inclusive. {@code pagado} = la reserva está CONFIRMADA.
     */
    List<BloqueReservado> bloquesReservados(Long espacioId, LocalDate desde, LocalDate hasta);
}
