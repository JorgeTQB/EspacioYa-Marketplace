package pe.edu.ulima.espacioya.espacios.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.espacios.domain.BloqueoFecha;

public interface BloqueoFechaRepository extends JpaRepository<BloqueoFecha, Long> {

    boolean existsByEspacioIdAndFecha(Long espacioId, LocalDate fecha);

    Optional<BloqueoFecha> findByEspacioIdAndFecha(Long espacioId, LocalDate fecha);

    List<BloqueoFecha> findByEspacioIdAndFechaGreaterThanEqualOrderByFechaAsc(Long espacioId, LocalDate desde);

    @Query("select b.espacioId from BloqueoFecha b where b.espacioId in :ids and b.fecha = :fecha")
    List<Long> espaciosBloqueados(@Param("ids") Collection<Long> ids, @Param("fecha") LocalDate fecha);
}
