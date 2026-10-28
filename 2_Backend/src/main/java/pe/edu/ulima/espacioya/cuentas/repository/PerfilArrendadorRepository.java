package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;

public interface PerfilArrendadorRepository extends JpaRepository<PerfilArrendador, Long> {

    Optional<PerfilArrendador> findByHashDocumento(String hashDocumento);

    // Bloquea la fila: dos envíos de documentos simultáneos se ordenan y el segundo ve EN_REVISION (HU-05).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PerfilArrendador p where p.usuarioId = :id")
    Optional<PerfilArrendador> findParaActualizar(@Param("id") Long id);
}
