package pe.edu.ulima.espacioya.busqueda.service;

import java.time.Clock;
import java.time.Instant;

import pe.edu.ulima.espacioya.busqueda.domain.MotivoNoResenable;
import pe.edu.ulima.espacioya.busqueda.domain.ReglasResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/**
 * ★ Decorator, rol <b>ConcreteComponent</b> (HU-24, R52): la regla del negocio y nada más. Reserva propia (404),
 * reseña previa, plazo y guardar con {@link ResenaDAO}. No sanitiza, no abre la transacción (supone la de
 * {@link PublicacionTransaccional}) ni audita. También es <b>BusinessObject</b> del DAO.
 */
public final class PublicacionResenaBase implements PublicacionResena {

    private final ResenaDAO resenas;
    private final ReservasParaResena reservas;
    private final Clock reloj;

    public PublicacionResenaBase(ResenaDAO resenas, ReservasParaResena reservas, Clock reloj) {
        this.resenas = resenas;
        this.reservas = reservas;
        this.reloj = reloj;
    }

    @Override
    public Resena publicar(SolicitudResena solicitud) {
        var reserva = reservas.delUsuario(solicitud.usuarioId(), solicitud.reservaId());
        // Antes que el plazo: si ya la reseñó, esa es la causa real (R12, DT-16).
        if (resenas.existeParaReserva(reserva.id())) {
            throw new ReglaNegocioException(ResenaService.MENSAJE_YA_RESENADA);
        }
        var ahora = Instant.now(reloj);
        ReglasResena.motivoNoResenable(reserva.confirmada(), reserva.finEn(), ahora).ifPresent(motivo -> {
            throw new ReglaNegocioException(mensajeDe(motivo));
        });
        return resenas.guardar(Resena.nueva(reserva.id(), solicitud.usuarioId(), reserva.espacioId(),
                solicitud.calificacion(), solicitud.comentario(), ahora));
    }

    private static String mensajeDe(MotivoNoResenable motivo) {
        return switch (motivo) {
            case NO_USADA -> ResenaService.MENSAJE_NO_RESENABLE;
            case PLAZO_VENCIDO -> ResenaService.MENSAJE_PLAZO_VENCIDO;
        };
    }
}
