package pe.edu.ulima.espacioya.busqueda.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.MotivoNoResenable;
import pe.edu.ulima.espacioya.busqueda.domain.ReglasResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.PaginaResenasDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaCreadaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenasPendientesDTO;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaRepository;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.texto.SanitizadorTexto;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaResena;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/**
 * Reseñas de reservas usadas (HU-24, RN-13). Clase concreta: la interfaz pública para {@code administracion} es
 * {@link ModeracionResenas} (HU-27). Sin
 * bloqueos: una {@code CONFIRMADA} terminada ya no cambia de estado y la doble reseña la frena el índice único (DT-7).
 */
@Service
public class ResenaService {

    public static final String ACCION_RESENA_CREADA = "RESENA_CREADA";
    public static final int TAMANO_PAGINA = 10;

    public static final String MENSAJE_NO_RESENABLE = "Solo puedes reseñar reservas confirmadas que ya pasaron.";
    public static final String MENSAJE_PLAZO_VENCIDO = "El plazo de 14 días para reseñar esta reserva ya terminó.";
    public static final String MENSAJE_YA_RESENADA = "Ya reseñaste esta reserva.";

    private static final String ROL_QUE_RESENA = "USUARIO";
    private static final String CAMPO_COMENTARIO = "comentario";
    private static final String ENTIDAD_RESENA = "resena";
    private static final String RESTRICCION_UNA_POR_RESERVA = "uq_resena_reserva";
    private static final Sort MAS_NUEVAS_PRIMERO = Sort.by(Sort.Direction.DESC, "creadoEn", "id");

    private final ResenaRepository resenas;
    private final ReservasParaResena reservas;
    private final CatalogoEspaciosService catalogo;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public ResenaService(ResenaRepository resenas, ReservasParaResena reservas, CatalogoEspaciosService catalogo,
                         AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj) {
        this.resenas = resenas;
        this.reservas = reservas;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // Sin @Transactional: con él, la violación del índice llegaría al confirmar y no se podría traducir (DT-6).
    public ResenaCreadaDTO crear(UsuarioAutenticado usuario, CrearResenaDTO datos, String ip) {
        exigirRol(usuario);
        var comentario = SanitizadorTexto.limpiar(datos.comentario());
        if (comentario.isEmpty()) {
            throw new ReglaNegocioException(CAMPO_COMENTARIO, CrearResenaDTO.M_COMENTARIO);
        }
        try {
            return transacciones.execute(estado -> crearEnTransaccion(usuario, datos, comentario, ip));
        } catch (DataIntegrityViolationException conflicto) {
            if (esReservaYaResenada(conflicto)) {
                throw new ReglaNegocioException(MENSAJE_YA_RESENADA);
            }
            throw conflicto;
        }
    }

    @Transactional(readOnly = true)
    public ResenasPendientesDTO pendientes(UsuarioAutenticado usuario) {
        exigirRol(usuario);
        var ahora = Instant.now(reloj);
        var resenadas = resenas.reservasResenadas(usuario.usuarioId());
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
        var solicitud = PageRequest.of(numeroPagina - 1, TAMANO_PAGINA, MAS_NUEVAS_PRIMERO);
        // Spring Data rechaza un offset mayor que un int, pero esa página es válida: no tiene nada que mostrar (R34).
        if (solicitud.getOffset() > Integer.MAX_VALUE) {
            return new PaginaResenasDTO(numeroPagina, List.of(), false);
        }
        var trozo = resenas.findByEspacioIdAndEstado(id, EstadoResena.VISIBLE, solicitud);
        var publicas = trozo.getContent().stream().map(ResenaService::aPublica).toList();
        return new PaginaResenasDTO(numeroPagina, publicas, trozo.hasNext());
    }

    @Transactional(readOnly = true)
    public ResumenCalificaciones resumen(Long espacioId) {
        var totales = resenas.totales(espacioId, EstadoResena.VISIBLE);
        return ResumenCalificaciones.de(totales.getCantidad(), totales.getSuma());
    }

    // Antes de leer nada: R10 y R26 piden el 403 sin tocar las reservas.
    private static void exigirRol(UsuarioAutenticado usuario) {
        if (!ROL_QUE_RESENA.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private ResenaCreadaDTO crearEnTransaccion(UsuarioAutenticado usuario, CrearResenaDTO datos, String comentario,
                                               String ip) {
        var reserva = reservas.delUsuario(usuario.usuarioId(), datos.reservaId());
        // Antes que el plazo: si ya la reseñó, esa es la causa real (R12, DT-16).
        if (resenas.existsByReservaId(reserva.id())) {
            throw new ReglaNegocioException(MENSAJE_YA_RESENADA);
        }
        var ahora = Instant.now(reloj);
        ReglasResena.motivoNoResenable(reserva.confirmada(), reserva.finEn(), ahora).ifPresent(motivo -> {
            throw new ReglaNegocioException(mensajeDe(motivo));
        });
        var resena = resenas.saveAndFlush(Resena.nueva(reserva.id(), usuario.usuarioId(), reserva.espacioId(),
                datos.calificacion(), comentario, ahora));
        auditar(usuario, resena, ip);
        return new ResenaCreadaDTO(resena.getId(), resena.getReservaId(), resena.getCalificacion(),
                resena.getComentario(), fechaDe(resena));
    }

    private void auditar(UsuarioAutenticado usuario, Resena resena, String ip) {
        var detalle = "reservaId=" + resena.getReservaId() + ";espacioId=" + resena.getEspacioId() + ";calificacion="
                + resena.getCalificacion();
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_RESENA_CREADA, ENTIDAD_RESENA,
                resena.getId(), ip, detalle));
    }

    private static String mensajeDe(MotivoNoResenable motivo) {
        return switch (motivo) {
            case NO_USADA -> MENSAJE_NO_RESENABLE;
            case PLAZO_VENCIDO -> MENSAJE_PLAZO_VENCIDO;
        };
    }

    private static ResenaPublicaDTO aPublica(Resena resena) {
        return new ResenaPublicaDTO(resena.getId(), resena.getCalificacion(), resena.getComentario(), fechaDe(resena));
    }

    private static LocalDate fechaDe(Resena resena) {
        return LocalDate.ofInstant(resena.getCreadoEn(), ZonaHoraria.LIMA);
    }

    // Por el nombre de la restricción: otro fallo de integridad no debe disfrazarse de reseña repetida (R14).
    private static boolean esReservaYaResenada(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(RESTRICCION_UNA_POR_RESERVA);
    }
}
