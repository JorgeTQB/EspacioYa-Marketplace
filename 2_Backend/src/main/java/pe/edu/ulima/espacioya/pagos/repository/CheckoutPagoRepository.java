package pe.edu.ulima.espacioya.pagos.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.pagos.domain.CheckoutPago;

public interface CheckoutPagoRepository extends JpaRepository<CheckoutPago, Long> {

    Optional<CheckoutPago> findByReservaId(Long reservaId);

    boolean existsByReservaId(Long reservaId);
}
