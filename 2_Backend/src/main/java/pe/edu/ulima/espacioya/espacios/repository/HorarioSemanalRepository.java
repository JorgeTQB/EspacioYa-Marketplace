package pe.edu.ulima.espacioya.espacios.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.espacios.domain.HorarioSemanal;

public interface HorarioSemanalRepository extends JpaRepository<HorarioSemanal, Long> {

    List<HorarioSemanal> findByEspacioIdOrderByDiaSemanaAscHoraInicioAsc(Long espacioId);

    List<HorarioSemanal> findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(Long espacioId, short diaSemana);

    // Masivo y en el acto: con deleteAll + saveAll, Hibernate ejecutaría los INSERT antes que los DELETE y
    // chocaría con uq_horario_semanal_inicio al conservar una franja que empieza a la misma hora.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HorarioSemanal h where h.espacioId = :espacioId")
    int borrarDelEspacio(@Param("espacioId") Long espacioId);
}
