package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ulima.espacioya.cuentas.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correoNormalizado);

    // Bloquea la fila: dos admins que suspenden o reactivan la misma cuenta se ordenan y el segundo ve el estado
    // nuevo (HU-09, R38).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findParaActualizar(@Param("id") Long id);

    // El patrón llega en minúsculas y con %, _ y \ escapados (HU-09, R4). El correo ya se guarda en minúsculas.
    @Query("""
            select u from Usuario u
            where u.correo like :patron escape '\\'
               or lower(u.nombre) like :patron escape '\\'
            order by u.correo asc
            """)
    List<Usuario> buscar(@Param("patron") String patron, Limit limite);

    @Query("""
            select u.id from Usuario u
            where u.rol = pe.edu.ulima.espacioya.cuentas.domain.Rol.ARRENDADOR
              and u.estado <> pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario.ACTIVA
            """)
    List<Long> findIdsArrendadoresNoActivos();
}
