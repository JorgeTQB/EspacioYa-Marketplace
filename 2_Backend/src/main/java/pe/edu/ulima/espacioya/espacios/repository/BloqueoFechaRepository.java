package pe.edu.ulima.espacioya.espacios.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.espacios.domain.BloqueoFecha;

public interface BloqueoFechaRepository extends JpaRepository<BloqueoFecha, Long> {

    boolean existsByEspacioIdAndFecha(Long espacioId, LocalDate fecha);

    Optional<BloqueoFecha> findByEspacioIdAndFecha(Long espacioId, LocalDate fecha);

    List<BloqueoFecha> findByEspacioIdAndFechaGreaterThanEqualOrderByFechaAsc(Long espacioId, LocalDate desde);
}
