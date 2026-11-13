package pe.edu.ulima.espacioya.busqueda.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.busqueda.domain.ReglasResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.PaginaResenasDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaCreadaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenasPendientesDTO;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaResena;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/**
 * Reseñas de reservas usadas (HU-24, RN-13). Clase concreta: la interfaz pública para {@code administracion} es
 * {@link ModeracionResenas} (HU-27). Para crear es el <b>Client</b> del ★ Decorator: comprueba el rol y delega en la
 * cadena {@link PublicacionResena} (R44). Para leer es <b>BusinessObject</b> del ★ DAO: solo conoce {@link ResenaDAO}
 * (R53). Sin bloqueos: una {@code CONFIRMADA} terminada ya no cambia de estado y la doble reseña la frena el índice
 * único (DT-7).
 */
@Service
public class ResenaService {

    public static final String ACCION_RESENA_CREADA = "RESENA_CREADA";
    public static final int TAMANO_PAGINA = 10;

    public static final String MENSAJE_NO_RESENABLE = "Solo puedes reseñar reservas confirmadas que ya pasaron.";
    public static final String MENSAJE_PLAZO_VENCIDO = "El plazo de 14 días para reseñar esta reserva ya terminó.";
    public static final String MENSAJE_YA_RESENADA = "Ya reseñaste esta reserva.";

    private static final String ROL_QUE_RESENA = "USUARIO";

    private final PublicacionResena publicacion;
    private final ResenaDAO resenas;
    private final ReservasParaResena reservas;
    private final CatalogoEspaciosService catalogo;
    private final Clock reloj;

    public ResenaService(PublicacionResena publicacion, ResenaDAO resenas, ReservasParaResena reservas,
                         CatalogoEspaciosService catalogo, Clock reloj) {
        this.publicacion = publicacion;
        this.resenas = resenas;
        this.reservas = reservas;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    // Sin @Transactional: la transacción la abre PublicacionTransaccional, dentro de la cadena (DT-6, DT-21).
    public ResenaCreadaDTO crear(UsuarioAutenticado usuario, CrearResenaDTO datos, String ip) {
        exigirRol(usuario);
        var resena = publicacion.publicar(new SolicitudResena(usuario.usuarioId(), datos.reservaId(),
                datos.calificacion(), datos.comentario(), ip));
        return new ResenaCreadaDTO(resena.getId(), resena.getReservaId(), resena.getCalificacion(),
                resena.getComentario(), fechaDe(resena));
    }

    @Transactional(readOnly = true)
    public ResenasPendientesDTO pendientes(UsuarioAutenticado usuario) {
        exigirRol(usuario);
        var ahora = Instant.now(reloj);
        var resenadas = resenas.reservasResenadasPor(usuario.usuarioId());
        var ids = reservas.confirmadasDe(usuario.usuarioId()).stream()
                .filter(reserva -> !resenadas.contains(reserva.id()))
                .filter(reserva -> ReglasResena.motivoNoResenable(reserva.confirmada(), reserva.finEn(), ahora)
                        .isEmpty())
                .map(ReservaParaResena::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new ResenasPendientesDTO(ids);
    }

    @Transactional(readOnly = true)
    public PaginaResenasDTO deEspacio(String espacioId, String pagina) {
        // Primero el recurso y después la página: un espacio invisible con página mala sigue siendo 404 (R33).
        var id = FichaService.idValido(espacioId);
        if (id == null || !catalogo.esVisible(id)) {
            throw new RecursoNoEncontradoException();
        }
        var numeroPagina = BusquedaService.pagina(pagina);
        if (numeroPagina == 0) {
            throw new ReglaNegocioException(BusquedaService.CAMPO_PAGINA, BusquedaService.MENSAJE_PAGINA_INVALIDA);
        }
        var trozo = resenas.visiblesDeEspacio(id, numeroPagina, TAMANO_PAGINA);
        var publicas = trozo.resenas().stream().map(ResenaService::aPublica).toList();
        return new PaginaResenasDTO(numeroPagina, publicas, trozo.hayMas());
    }

    @Transactional(readOnly = true)
    public ResumenCalificaciones resumen(Long espacioId) {
        return resenas.resumenVisibles(espacioId);
    }

    // Antes de leer nada: R10 y R26 piden el 403 sin tocar las reservas.
    private static void exigirRol(UsuarioAutenticado usuario) {
        if (!ROL_QUE_RESENA.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private static ResenaPublicaDTO aPublica(Resena resena) {
        return new ResenaPublicaDTO(resena.getId(), resena.getCalificacion(), resena.getComentario(), fechaDe(resena));
    }

    private static LocalDate fechaDe(Resena resena) {
        return LocalDate.ofInstant(resena.getCreadoEn(), ZonaHoraria.LIMA);
    }
}
