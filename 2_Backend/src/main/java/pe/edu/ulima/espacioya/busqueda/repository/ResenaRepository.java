package pe.edu.ulima.espacioya.busqueda.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

    boolean existsByReservaId(Long reservaId);

    @Query("select r.reservaId from Resena r where r.usuarioId = :usuarioId")
    Set<Long> reservasResenadas(@Param("usuarioId") Long usuarioId);

    Slice<Resena> findByEspacioIdAndEstado(Long espacioId, EstadoResena estado, Pageable pagina);

    @Query("select count(r) as cantidad, coalesce(sum(r.calificacion), 0) as suma from Resena r "
            + "where r.espacioId = :espacioId and r.estado = :estado")
    TotalesResena totales(@Param("espacioId") Long espacioId, @Param("estado") EstadoResena estado);

    List<Resena> findByUsuarioIdOrderByCreadoEnAscIdAsc(Long usuarioId);

    @Modifying
    @Query("update Resena r set r.comentario = :comentario where r.usuarioId = :usuarioId")
    int reemplazarComentarios(@Param("usuarioId") Long usuarioId, @Param("comentario") String comentario);
}
