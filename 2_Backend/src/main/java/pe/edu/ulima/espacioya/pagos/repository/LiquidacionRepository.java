package pe.edu.ulima.espacioya.pagos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long> {

    /** Clave del bloqueo consultivo que serializa las generaciones (HU-19 DT-7). */
    long CLAVE_GENERACION = 190_019L;

    /**
     * Bloqueo consultivo de PostgreSQL hasta el fin de la transacción del llamador (DT-7): una segunda generación
     * espera al commit de la primera. No deja filas; se libera solo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
    long bloquearGeneracion(@Param("clave") long clave);

    /** Toma la fila hasta el fin de la transacción: dos marcados a la vez se serializan (R32). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Liquidacion l where l.id = :id")
    Optional<Liquidacion> findParaActualizar(@Param("id") Long id);

    Slice<Liquidacion> findByEstadoIn(Collection<EstadoLiquidacion> estados, Pageable pagina);

    Slice<Liquidacion> findByArrendadorId(Long arrendadorId, Pageable pagina);

    boolean existsByArrendadorIdAndEstadoIn(Long arrendadorId, Collection<EstadoLiquidacion> estados);

    // HU-22 R8: todas las del arrendador para su exportación, de la más reciente a la más antigua.
    List<Liquidacion> findByArrendadorIdOrderByGeneradaEnDescIdDesc(Long arrendadorId);
}
