package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.DesafioMfa;

public interface DesafioMfaRepository extends JpaRepository<DesafioMfa, Long> {

    // Bloquea la fila: dos envíos del mismo desafío se ordenan y solo uno crea sesión (HU-17 DT-11).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesafioMfa d where d.hashToken = :hashToken")
    Optional<DesafioMfa> findParaActualizar(@Param("hashToken") String hashToken);

    // HU-22 R30: al eliminar la cuenta no queda ningún desafío canjeable.
    @Modifying(flushAutomatically = true)
    @Query("delete from DesafioMfa d where d.usuarioId = :usuarioId")
    int borrarDeUsuario(@Param("usuarioId") Long usuarioId);
}
