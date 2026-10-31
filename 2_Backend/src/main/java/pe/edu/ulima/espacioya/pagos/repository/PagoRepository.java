package pe.edu.ulima.espacioya.pagos.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByIdTransaccionPasarela(String idTransaccionPasarela);

    long countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(Long reservaId, EstadoPago estado, Instant desde);

    long countByReservaIdAndEstado(Long reservaId, EstadoPago estado);

    Optional<Pago> findFirstByReservaIdOrderByCreadoEnDescIdDesc(Long reservaId);

    // Solo con PAGADO (HU-15): uq_pago_reserva_pagado garantiza como mucho uno por reserva.
    Optional<Pago> findByReservaIdAndEstado(Long reservaId, EstadoPago estado);
}
