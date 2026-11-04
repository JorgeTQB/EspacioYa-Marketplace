package pe.edu.ulima.espacioya.reservas.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.reservas.domain.ReservaBloque;

public interface ReservaBloqueRepository extends JpaRepository<ReservaBloque, Long> {

    List<ReservaBloque> findByReservaIdOrderByHoraInicioAsc(Long reservaId);

    // Sin filtro por activo: el horario de una pasada sale de sus filas liberadas (HU-12 DT-10, HU-14 R8).
    @Query("select new pe.edu.ulima.espacioya.reservas.repository.HorarioReserva("
            + "b.reservaId, min(b.fecha), min(b.horaInicio), max(b.horaInicio)) "
            + "from ReservaBloque b where b.reservaId in :reservaIds group by b.reservaId")
    List<HorarioReserva> horarios(@Param("reservaIds") Collection<Long> reservaIds);

    // Llega a la base en el acto: con entidades sucias, Hibernate insertaría los bloques nuevos antes de liberar
    // estos y chocaría con uq_reserva_bloque_activo (HU-12 DT-4).
    @Modifying(flushAutomatically = true)
    @Query("update ReservaBloque b set b.activo = false where b.reservaId = :reservaId and b.activo = true")
    int desactivarDeReserva(@Param("reservaId") Long reservaId);

    @Query("select new pe.edu.ulima.espacioya.reservas.repository.BloqueActivo("
            + "b.espacioId, b.fecha, b.horaInicio, r.estado) "
            + "from ReservaBloque b join Reserva r on r.id = b.reservaId "
            + "where b.activo = true and b.espacioId = :espacioId and b.fecha between :desde and :hasta "
            + "and (r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.CONFIRMADA "
            + "or (r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.PENDIENTE_PAGO "
            + "and r.expiraEn > :ahora))")
    List<BloqueActivo> bloquesActivos(@Param("espacioId") Long espacioId, @Param("desde") LocalDate desde,
                                      @Param("hasta") LocalDate hasta, @Param("ahora") Instant ahora);

    @Query("select new pe.edu.ulima.espacioya.reservas.repository.BloqueActivo("
            + "b.espacioId, b.fecha, b.horaInicio, r.estado) "
            + "from ReservaBloque b join Reserva r on r.id = b.reservaId "
            + "where b.activo = true and b.espacioId in :espacioIds and b.fecha = :fecha "
            + "and (r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.CONFIRMADA "
            + "or (r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.PENDIENTE_PAGO "
            + "and r.expiraEn > :ahora))")
    List<BloqueActivo> bloquesActivosDelDia(@Param("espacioIds") Collection<Long> espacioIds,
                                            @Param("fecha") LocalDate fecha, @Param("ahora") Instant ahora);
}
