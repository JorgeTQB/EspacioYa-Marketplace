package pe.edu.ulima.espacioya.busqueda.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;

/**
 * DAO de la tabla {@code resena} (★ HU-24, DT-25): la única puerta de {@code busqueda} a las reseñas (R53). Habla el
 * idioma del negocio y esconde Spring Data: no recibe {@code Pageable} ni devuelve {@code Slice} ni proyecciones. No
 * abre transacciones: las ponen sus llamadores, como en {@code ReservaDAO}.
 */
public interface ResenaDAO {

    /** Si la reserva ya tiene una reseña, en cualquier estado (R12). */
    boolean existeParaReserva(Long reservaId);

    /**
     * Inserta la reseña de inmediato ({@code saveAndFlush}): la violación de {@code uq_resena_reserva} sale de esta
     * misma llamada, dentro de la transacción del llamador (R57).
     */
    Resena guardar(Resena nueva);

    /** Ids de las reservas que el usuario ya reseñó, en cualquier estado (R25). */
    Set<Long> reservasResenadasPor(Long usuarioId);

    /**
     * Página {@code pagina} (desde 1) de las reseñas {@code VISIBLE} del espacio, por {@code creado_en} e {@code id}
     * descendentes (R54); vacía y sin consultar si el offset no cabe en un {@code int} (R55).
     */
    TrozoResenas visiblesDeEspacio(Long espacioId, int pagina, int tamano);

    /** Promedio y total calculados solo con las reseñas {@code VISIBLE} del espacio (R56). */
    ResumenCalificaciones resumenVisibles(Long espacioId);

    /** HU-22: todas las reseñas del usuario, en cualquier estado, por {@code creado_en} e {@code id} ascendentes. */
    List<Resena> delUsuario(Long usuarioId);

    /** HU-22: reemplaza el comentario por {@link Resena#COMENTARIO_ELIMINADO}; devuelve cuántas cambió. */
    int anonimizarComentarios(Long usuarioId);

    /** HU-27. */
    Optional<Resena> buscar(Long id);

    /** HU-27: las que existan entre esos ids, sin orden. */
    List<Resena> buscarVarias(Collection<Long> ids);

    /** HU-27: con bloqueo {@code PESSIMISTIC_WRITE}; exige una transacción activa. */
    Optional<Resena> buscarParaActualizar(Long id);
}
