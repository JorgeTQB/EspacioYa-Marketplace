package pe.edu.ulima.espacioya.cuentas.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
    // También serializa dos decisiones del admin sobre el mismo arrendador (HU-06, R39).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PerfilArrendador p where p.usuarioId = :id")
    Optional<PerfilArrendador> findParaActualizar(@Param("id") Long id);

    /** Arrendador de la cola de revisión con su fecha de envío (el menor {@code subido_en} de sus documentos). */
    record EnRevision(Long arrendadorId, Instant enviadoEn) {
    }

    // Las entidades de cuentas solo se relacionan por id, así que se une por igualdad de ids (HU-06, DT-8).
    @Query("""
            select new pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository$EnRevision(
                p.usuarioId, min(d.subidoEn))
            from PerfilArrendador p, Usuario u, DocumentoIdentidad d
            where u.id = p.usuarioId
              and d.arrendadorId = p.usuarioId
              and p.estadoIdentidad = pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad.EN_REVISION
              and u.estado = pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario.ACTIVA
              and u.rol = pe.edu.ulima.espacioya.cuentas.domain.Rol.ARRENDADOR
            group by p.usuarioId
            order by min(d.subidoEn) asc, p.usuarioId asc
            """)
    List<EnRevision> findEnRevision();

    /** De esos ids, los que tienen perfil con la identidad {@code VERIFICADA} (HU-19 D6). Solo ids, nunca datos. */
    @Query("select p.usuarioId from PerfilArrendador p where p.usuarioId in :ids "
            + "and p.estadoIdentidad = pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad.VERIFICADA")
    List<Long> idsConIdentidadVerificada(@Param("ids") Collection<Long> ids);
}
