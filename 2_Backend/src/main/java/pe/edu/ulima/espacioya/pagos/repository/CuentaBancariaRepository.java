package pe.edu.ulima.espacioya.pagos.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;

public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, Long> {

    Optional<CuentaBancaria> findByArrendadorId(Long arrendadorId);

    /** Toma la fila hasta el fin de la transacción: serializa dos {@code PUT} del mismo arrendador (R25). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaBancaria c where c.arrendadorId = :arrendadorId")
    Optional<CuentaBancaria> findParaActualizar(@Param("arrendadorId") Long arrendadorId);
}
