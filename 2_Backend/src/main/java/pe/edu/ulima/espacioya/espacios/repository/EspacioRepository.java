package pe.edu.ulima.espacioya.espacios.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;

// Las consultas del dueño filtran siempre por arrendador_id: ajeno e inexistente son lo mismo (HU-07 DT-8).
public interface EspacioRepository extends JpaRepository<Espacio, Long> {

    List<Espacio> findByArrendadorIdOrderByCreadoEnDesc(Long arrendadorId);

    Optional<Espacio> findByIdAndArrendadorId(Long id, Long arrendadorId);

    // Ordena publicar, editar y agregar o quitar fotos del mismo espacio (HU-07 DT-12).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Espacio e where e.id = :id and e.arrendadorId = :arrendadorId")
    Optional<Espacio> findParaActualizar(@Param("id") Long id, @Param("arrendadorId") Long arrendadorId);

    // El mismo bloqueo de fila, sin filtrar por dueño: quien reserva no es el dueño (HU-08 DT-10, HU-12 DT-2).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Espacio e where e.id = :id")
    Optional<Espacio> findParaReservar(@Param("id") Long id);

    // Búsqueda pública (HU-10 DT-4, DT-9): servida por ix_espacio_busqueda.
    @Query("select e from Espacio e where e.estado = :estado and e.tipo = :tipo and e.zona = :zona "
            + "order by e.precioHora asc, e.id asc")
    List<Espacio> buscarPublicados(@Param("estado") EstadoEspacio estado, @Param("tipo") TipoEspacio tipo,
                                   @Param("zona") String zona);

    // Solo se llama con un conjunto no vacío: "not in" con una colección vacía no es portable en JPQL (HU-09 D1).
    @Query("select e from Espacio e where e.estado = :estado and e.tipo = :tipo and e.zona = :zona "
            + "and e.arrendadorId not in :excluidos order by e.precioHora asc, e.id asc")
    List<Espacio> buscarPublicadosExcluyendo(@Param("estado") EstadoEspacio estado, @Param("tipo") TipoEspacio tipo,
                                             @Param("zona") String zona,
                                             @Param("excluidos") Collection<Long> excluidos);
}
