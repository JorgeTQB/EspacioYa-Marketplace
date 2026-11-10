package pe.edu.ulima.espacioya.administracion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    boolean existsByReservaId(Long reservaId);

    /** Toma la fila hasta el fin de la transacción: mensajes y resoluciones del mismo reclamo se serializan (DT-8). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reclamo r where r.id = :id")
    Optional<Reclamo> findParaActualizar(@Param("id") Long id);

    Slice<Reclamo> findByUsuarioId(Long usuarioId, Pageable pagina);

    Slice<Reclamo> findByArrendadorId(Long arrendadorId, Pageable pagina);

    Slice<Reclamo> findByEstado(EstadoReclamo estado, Pageable pagina);

    Slice<Reclamo> findByEstadoIn(Collection<EstadoReclamo> estados, Pageable pagina);

    @Query("select r.reservaId from Reclamo r where r.reservaId in :ids")
    Set<Long> reservasConReclamo(@Param("ids") Collection<Long> reservaIds);

    @Query("select r.reservaId from Reclamo r where r.reservaId in :ids "
            + "and r.estado = pe.edu.ulima.espacioya.administracion.domain.EstadoReclamo.ABIERTO")
    Set<Long> reservasConReclamoAbierto(@Param("ids") Collection<Long> reservaIds);

    List<Reclamo> findByUsuarioIdOrArrendadorIdOrderByAbiertoEnAscIdAsc(Long usuarioId, Long arrendadorId);

    @Query("select case when count(r) > 0 then true else false end from Reclamo r "
            + "where r.estado = pe.edu.ulima.espacioya.administracion.domain.EstadoReclamo.ABIERTO "
            + "and (r.usuarioId = :id or r.arrendadorId = :id)")
    boolean existeAbiertoDe(@Param("id") Long cuentaId);

    @Modifying(flushAutomatically = true)
    @Query("update Reclamo r set r.descripcion = :texto where r.usuarioId = :id")
    int reemplazarDescripciones(@Param("id") Long usuarioId, @Param("texto") String texto);
}
