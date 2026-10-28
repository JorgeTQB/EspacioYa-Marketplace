package pe.edu.ulima.espacioya.cuentas.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Token;

public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByHashTokenAndTipo(String hashToken, TipoToken tipo);

    // UPDATE condicional: si otra confirmación ya marcó el token, este no afecta filas (uso único, HU-04 DT-3).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Token t set t.usadoEn = :ahora where t.id = :id and t.usadoEn is null and t.expiraEn > :ahora")
    int marcarUsado(@Param("id") Long id, @Param("ahora") Instant ahora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Token t set t.usadoEn = :ahora where t.usuarioId = :usuarioId and t.tipo = :tipo "
            + "and t.usadoEn is null and t.expiraEn > :ahora")
    int anularPendientes(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoToken tipo,
                         @Param("ahora") Instant ahora);
}
