package pe.edu.ulima.espacioya.reservas.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByIdAndUsuarioId(Long id, Long usuarioId);

    // HU-14: solo los usa ReservaDAOJpa; el servicio pide las reservas por el DAO, siempre con su dueño.
    List<Reserva> findByUsuarioId(Long usuarioId);

    List<Reserva> findByEspacioIdInAndEstado(Collection<Long> espacioIds, EstadoReserva estado);

    @Query("select count(r) from Reserva r "
            + "where r.usuarioId = :usuarioId "
            + "and r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.PENDIENTE_PAGO "
            + "and r.expiraEn > :ahora")
    long contarVigentes(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

    @Query("select r.id from Reserva r "
            + "where r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.PENDIENTE_PAGO "
            + "and r.expiraEn <= :ahora order by r.id")
    List<Long> findIdsVencidas(@Param("ahora") Instant ahora);

    // HU-25: candidatas del recordatorio; la regla exacta de 24 h la aplica ReglaRecordatorio en Java (alternativa 4).
    @Query("select r.id from Reserva r "
            + "where r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.CONFIRMADA "
            + "and r.recordatorioEnviadoEn is null "
            + "and exists (select 1 from ReservaBloque b where b.reservaId = r.id and b.activo = true "
            + "and b.fecha between :desde and :hasta) order by r.id")
    List<Long> findIdsPorRecordar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> findParaActualizar(@Param("id") Long id);

    // Una proyección y no la entidad: si no, findParaActualizar devolvería la copia ya cargada, sin releer el estado.
    @Query("select r.espacioId from Reserva r where r.id = :id")
    Optional<Long> findEspacioIdById(@Param("id") Long id);

    // Las vencidas que aún retienen estas horas: el índice parcial no entiende el tiempo (HU-12 DT-3).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r "
            + "where r.estado = pe.edu.ulima.espacioya.reservas.domain.EstadoReserva.PENDIENTE_PAGO "
            + "and r.expiraEn <= :ahora "
            + "and r.id in (select b.reservaId from ReservaBloque b "
            + "where b.activo = true and b.espacioId = :espacioId and b.fecha = :fecha and b.horaInicio in :horas)")
    List<Reserva> findVencidasQueRetienen(@Param("espacioId") Long espacioId, @Param("fecha") LocalDate fecha,
                                          @Param("horas") Collection<Short> horas, @Param("ahora") Instant ahora);
}
