package pe.edu.ulima.espacioya.administracion.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    /** Cupo de HU-27: reportes del reportante con {@code creado_en} estrictamente posterior a {@code desde}. */
    long countByReportanteIdAndCreadoEnAfter(Long reportanteId, Instant desde);

    boolean existsByReportanteIdAndEspacioId(Long reportanteId, Long espacioId);

    boolean existsByReportanteIdAndResenaId(Long reportanteId, Long resenaId);

    /** Reportes hermanos de un espacio, bloqueados en orden de id (DT-7). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reporte r where r.espacioId = :id "
            + "and r.estado = pe.edu.ulima.espacioya.administracion.domain.EstadoReporte.POR_REVISAR order by r.id")
    List<Reporte> bloquearPorRevisarDeEspacio(@Param("id") Long espacioId);

    /** Reportes hermanos de una reseña, bloqueados en orden de id (DT-7). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reporte r where r.resenaId = :id "
            + "and r.estado = pe.edu.ulima.espacioya.administracion.domain.EstadoReporte.POR_REVISAR order by r.id")
    List<Reporte> bloquearPorRevisarDeResena(@Param("id") Long resenaId);

    long countByEspacioIdAndEstado(Long espacioId, EstadoReporte estado);

    long countByResenaIdAndEstado(Long resenaId, EstadoReporte estado);

    Slice<Reporte> findByEstado(EstadoReporte estado, Pageable pagina);

    Slice<Reporte> findByEstadoIn(Collection<EstadoReporte> estados, Pageable pagina);

    List<Reporte> findByReportanteIdOrderByCreadoEnAscIdAsc(Long reportanteId);
}
