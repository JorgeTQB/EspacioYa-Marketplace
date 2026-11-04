package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.Mfa;

public interface MfaRepository extends JpaRepository<Mfa, Long> {

    // Orden fijo de bloqueos desafio_mfa → usuario → mfa (HU-17 DT-11).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mfa m where m.usuarioId = :usuarioId")
    Optional<Mfa> findParaActualizar(@Param("usuarioId") Long usuarioId);

    boolean existsByUsuarioIdAndActivoTrue(Long usuarioId);
}
