package pe.edu.ulima.espacioya.busqueda.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;

/**
 * Implementación JPA de {@link ResenaDAO} (★ HU-24, DT-25). Es el único lugar que conoce {@link ResenaRepository}: el
 * orden de la ficha, el filtro {@code VISIBLE}, el tope de offset y el texto de anonimización viven aquí y no en cada
 * llamador.
 */
@Repository
class ResenaDAOJpa implements ResenaDAO {

    static final Sort MAS_NUEVAS_PRIMERO = Sort.by(Sort.Direction.DESC, "creadoEn", "id");

    private final ResenaRepository resenas;

    ResenaDAOJpa(ResenaRepository resenas) {
        this.resenas = resenas;
    }

    @Override
    public boolean existeParaReserva(Long reservaId) {
        return resenas.existsByReservaId(reservaId);
    }

    @Override
    public Resena guardar(Resena nueva) {
        return resenas.saveAndFlush(nueva);
    }

    @Override
    public Set<Long> reservasResenadasPor(Long usuarioId) {
        return resenas.reservasResenadas(usuarioId);
    }

    @Override
    public TrozoResenas visiblesDeEspacio(Long espacioId, int pagina, int tamano) {
        var solicitud = PageRequest.of(pagina - 1, tamano, MAS_NUEVAS_PRIMERO);
        // Spring Data rechaza un offset mayor que un int, pero esa página es válida: no tiene nada que mostrar (R55).
        if (solicitud.getOffset() > Integer.MAX_VALUE) {
            return TrozoResenas.VACIO;
        }
        var trozo = resenas.findByEspacioIdAndEstado(espacioId, EstadoResena.VISIBLE, solicitud);
        return new TrozoResenas(trozo.getContent(), trozo.hasNext());
    }

    @Override
    public ResumenCalificaciones resumenVisibles(Long espacioId) {
        var totales = resenas.totales(espacioId, EstadoResena.VISIBLE);
        return ResumenCalificaciones.de(totales.getCantidad(), totales.getSuma());
    }

    @Override
    public List<Resena> delUsuario(Long usuarioId) {
        return resenas.findByUsuarioIdOrderByCreadoEnAscIdAsc(usuarioId);
    }

    @Override
    public int anonimizarComentarios(Long usuarioId) {
        return resenas.reemplazarComentarios(usuarioId, Resena.COMENTARIO_ELIMINADO);
    }

    @Override
    public Optional<Resena> buscar(Long id) {
        return resenas.findById(id);
    }

    @Override
    public List<Resena> buscarVarias(Collection<Long> ids) {
        return resenas.findAllById(Set.copyOf(ids));
    }

    @Override
    public Optional<Resena> buscarParaActualizar(Long id) {
        return resenas.findParaActualizar(id);
    }
}
