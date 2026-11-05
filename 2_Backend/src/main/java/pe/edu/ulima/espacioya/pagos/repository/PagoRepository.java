package pe.edu.ulima.espacioya.pagos.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByIdTransaccionPasarela(String idTransaccionPasarela);

    long countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(Long reservaId, EstadoPago estado, Instant desde);

    long countByReservaIdAndEstado(Long reservaId, EstadoPago estado);

    Optional<Pago> findFirstByReservaIdOrderByCreadoEnDescIdDesc(Long reservaId);

    // Solo con PAGADO (HU-15): uq_pago_reserva_pagado garantiza como mucho uno por reserva.
    Optional<Pago> findByReservaIdAndEstado(Long reservaId, EstadoPago estado);

    /**
     * Pagos que cuentan para liquidar (HU-19 DT-6, S3, D2): {@code PAGADO}, sin ninguna fila de {@code reembolso}
     * (cualquier origen y estado), cuya reserva no figura en {@code liquidacion_detalle} y, si
     * {@code incluirSimulados} es falso, que no son de la pasarela simulada. Por reserva ascendente.
     */
    @Query("select p from Pago p where p.estado = pe.edu.ulima.espacioya.pagos.domain.EstadoPago.PAGADO "
            + "and not exists (select r.id from Reembolso r where r.pagoId = p.id) "
            + "and not exists (select d.id from LiquidacionDetalle d where d.reservaId = p.reservaId) "
            + "and (:incluirSimulados = true or p.idTransaccionPasarela not like 'sim-%') order by p.reservaId")
    List<Pago> pagadosSinLiquidar(@Param("incluirSimulados") boolean incluirSimulados);

    // HU-22 R6: los pagos de las reservas del titular, para su exportación.
    List<Pago> findByReservaIdInOrderByCreadoEnAscIdAsc(Collection<Long> reservaIds);
}
