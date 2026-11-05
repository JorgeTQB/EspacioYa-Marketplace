package pe.edu.ulima.espacioya.pagos.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.pagos.domain.OrigenReembolso;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.domain.ReembolsoPorAtender;

public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {

    // Serializa los intentos de un mismo reembolso (HU-16 DT-5): el segundo lo ve ya en proceso.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reembolso r where r.id = :id")
    Optional<Reembolso> findParaActualizar(@Param("id") Long id);

    boolean existsByPagoIdAndOrigen(Long pagoId, OrigenReembolso origen);

    /** FALLIDO, o PENDIENTE cuyo último intento (o creación) es anterior o igual a {@code limite} (HU-16 R28, R29). */
    @Query("""
            select new pe.edu.ulima.espacioya.pagos.domain.ReembolsoPorAtender(r.id, p.reservaId, r.monto, r.origen,
                   r.estado, r.intentos, r.creadoEn, r.ultimoIntentoEn)
            from Reembolso r join Pago p on p.id = r.pagoId
            where r.estado = pe.edu.ulima.espacioya.pagos.domain.EstadoReembolso.FALLIDO
               or (r.estado = pe.edu.ulima.espacioya.pagos.domain.EstadoReembolso.PENDIENTE
                   and coalesce(r.ultimoIntentoEn, r.creadoEn) <= :limite)
            order by r.creadoEn, r.id""")
    List<ReembolsoPorAtender> porAtender(@Param("limite") Instant limite);

    // HU-22 R6: los reembolsos de los pagos exportados.
    List<Reembolso> findByPagoIdInOrderByCreadoEnAscIdAsc(Collection<Long> pagoIds);
}
