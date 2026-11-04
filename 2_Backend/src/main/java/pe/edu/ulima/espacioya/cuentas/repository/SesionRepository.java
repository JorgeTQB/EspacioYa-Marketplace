package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.Sesion;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

    Optional<Sesion> findByHashToken(String hashToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Sesion s set s.revocada = true where s.usuarioId = :usuarioId and s.revocada = false")
    int revocarTodas(@Param("usuarioId") Long usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Sesion s set s.revocada = true "
            + "where s.usuarioId = :usuarioId and s.id <> :sesionId and s.revocada = false")
    int revocarOtras(@Param("usuarioId") Long usuarioId, @Param("sesionId") Long sesionId);
}
