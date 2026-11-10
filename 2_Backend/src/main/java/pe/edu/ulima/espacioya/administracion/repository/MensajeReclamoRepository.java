package pe.edu.ulima.espacioya.administracion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.administracion.domain.MensajeReclamo;

public interface MensajeReclamoRepository extends JpaRepository<MensajeReclamo, Long> {

    List<MensajeReclamo> findByReclamoIdOrderByFechaAscIdAsc(Long reclamoId);

    long countByReclamoIdAndAutorId(Long reclamoId, Long autorId);

    List<MensajeReclamo> findByAutorIdOrderByFechaAscIdAsc(Long autorId);

    @Modifying(flushAutomatically = true)
    @Query("update MensajeReclamo m set m.texto = :texto where m.autorId = :id")
    int reemplazarTextos(@Param("id") Long autorId, @Param("texto") String texto);
}
