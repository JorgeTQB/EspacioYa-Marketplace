package pe.edu.ulima.espacioya.espacios.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.espacios.domain.Espacio;

// Las consultas del dueño filtran siempre por arrendador_id: ajeno e inexistente son lo mismo (HU-07 DT-8).
public interface EspacioRepository extends JpaRepository<Espacio, Long> {

    List<Espacio> findByArrendadorIdOrderByCreadoEnDesc(Long arrendadorId);

    Optional<Espacio> findByIdAndArrendadorId(Long id, Long arrendadorId);

    // Ordena publicar, editar y agregar o quitar fotos del mismo espacio (HU-07 DT-12).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Espacio e where e.id = :id and e.arrendadorId = :arrendadorId")
    Optional<Espacio> findParaActualizar(@Param("id") Long id, @Param("arrendadorId") Long arrendadorId);
}
