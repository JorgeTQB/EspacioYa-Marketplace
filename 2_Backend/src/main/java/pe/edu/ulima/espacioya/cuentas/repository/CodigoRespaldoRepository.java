package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.CodigoRespaldo;

public interface CodigoRespaldoRepository extends JpaRepository<CodigoRespaldo, Long> {

    Optional<CodigoRespaldo> findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(Long usuarioId, String hashCodigo);

    long countByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CodigoRespaldo c where c.usuarioId = :usuarioId")
    int borrarDeUsuario(@Param("usuarioId") Long usuarioId);
}
