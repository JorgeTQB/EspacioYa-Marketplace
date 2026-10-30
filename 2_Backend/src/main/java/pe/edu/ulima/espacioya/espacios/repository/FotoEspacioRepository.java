package pe.edu.ulima.espacioya.espacios.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;

public interface FotoEspacioRepository extends JpaRepository<FotoEspacio, Long> {

    List<FotoEspacio> findByEspacioIdOrderByOrdenAsc(Long espacioId);

    Optional<FotoEspacio> findByIdAndEspacioId(Long id, Long espacioId);

    long countByEspacioId(Long espacioId);

    List<FotoEspacio> findByEspacioIdInAndOrden(Collection<Long> espacioIds, short orden);

    // Usa el índice de uq_foto_espacio_ruta (HU-07 R39, R54, R55).
    Optional<FotoEspacio> findByRutaArchivo(String rutaArchivo);
}
