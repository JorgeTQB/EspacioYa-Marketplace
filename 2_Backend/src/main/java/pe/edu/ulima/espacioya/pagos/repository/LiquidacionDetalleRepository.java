package pe.edu.ulima.espacioya.pagos.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;

public interface LiquidacionDetalleRepository extends JpaRepository<LiquidacionDetalle, Long> {

    /** Las líneas de varias liquidaciones en una sola consulta (sin N+1 al armar una página). */
    List<LiquidacionDetalle> findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(Collection<Long> liquidacionIds);

    /** HU-26 R8: una reserva ya liquidada no admite reclamo. */
    boolean existsByReservaId(Long reservaId);
}
