package pe.edu.ulima.espacioya.pagos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.MotivoNoDisponible;
import pe.edu.ulima.espacioya.pagos.domain.ReglasPago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;

/** Decide si una reserva se puede pagar y traduce el motivo a la respuesta (HU-13 R10–R15, R18). */
@Service
public class EvaluacionCobro {

    public static final String MENSAJE_PAGO_NO_DISPONIBLE =
            "Esta reserva ya no se puede pagar. Busca otro horario para reservar de nuevo.";

    private static final String ROL_QUE_PAGA = "USUARIO";

    private final ReservasPorCobrar reservas;
    private final PagoRepository pagos;
    private final Clock reloj;
    private final Duration margenCierre;

    public EvaluacionCobro(ReservasPorCobrar reservas, PagoRepository pagos, Clock reloj,
                           @Value("${espacioya.pagos.margen-cierre}") Duration margenCierre) {
        this.reservas = reservas;
        this.pagos = pagos;
        this.reloj = reloj;
        this.margenCierre = margenCierre;
    }

    /**
     * Comprueba en el orden de R18 y devuelve la reserva si se puede pagar.
     *
     * @throws AccesoDenegadoException si la cuenta no tiene el rol {@code USUARIO}.
     * @throws RecursoNoEncontradoException si el id está mal escrito, no existe o es de otro usuario.
     * @throws ReglaNegocioException si la reserva no es vigente, está en el último tramo o su espacio no se ve.
     * @throws DemasiadosIntentosException si tiene 3 o más rechazos recientes.
     */
    public ReservaPorCobrar exigirDisponible(UsuarioAutenticado usuario, String reservaId) {
        exigirRol(usuario);
        var reserva = propia(usuario, reservaId);
        var ahora = Instant.now(reloj);
        var motivo = motivo(reserva, rechazosRecientes(reserva, ahora), ahora);
        if (motivo == MotivoNoDisponible.INTENTOS_AGOTADOS) {
            throw new DemasiadosIntentosException();
        }
        if (motivo != null) {
            throw new ReglaNegocioException(MENSAJE_PAGO_NO_DISPONIBLE);
        }
        return reserva;
    }

    /** @throws AccesoDenegadoException si la cuenta no tiene el rol {@code USUARIO}. */
    public void exigirRol(UsuarioAutenticado usuario) {
        if (!ROL_QUE_PAGA.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    /**
     * Comprueba rol, propiedad y rechazos, en ese orden (R79). No mira vigencia, último tramo ni espacio (S15): son
     * reglas de iniciar un pago, y quien ya está en el checkout llega al caso de D2, que decide
     * {@code WebhookPagoService}.
     *
     * @throws AccesoDenegadoException si la cuenta no tiene el rol {@code USUARIO}.
     * @throws RecursoNoEncontradoException si el id está mal escrito, no existe o es de otro usuario.
     * @throws DemasiadosIntentosException si tiene 3 o más rechazos recientes.
     */
    public ReservaPorCobrar exigirCheckoutNoAgotado(UsuarioAutenticado usuario, String reservaId) {
        exigirRol(usuario);
        var reserva = propia(usuario, reservaId);
        if (ReglasPago.intentosAgotados(rechazosRecientes(reserva, Instant.now(reloj)))) {
            throw new DemasiadosIntentosException();
        }
        return reserva;
    }

    /** Lo que la pantalla necesita saber de una reserva propia. Solo lee (R21). */
    public ResumenCobro resumen(UsuarioAutenticado usuario, String reservaId) {
        var reserva = propia(usuario, reservaId);
        var ahora = Instant.now(reloj);
        var rechazos = rechazosRecientes(reserva, ahora);
        return new ResumenCobro(reserva.id(), ReglasPago.intentosAgotados(rechazos),
                motivo(reserva, rechazos, ahora) == null);
    }

    private ReservaPorCobrar propia(UsuarioAutenticado usuario, String reservaId) {
        var id = ReglasPago.idDeReserva(reservaId);
        if (id == null) {
            throw new RecursoNoEncontradoException();
        }
        return reservas.deUsuario(id, usuario.usuarioId()).orElseThrow(RecursoNoEncontradoException::new);
    }

    private long rechazosRecientes(ReservaPorCobrar reserva, Instant ahora) {
        return pagos.countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(reserva.id(), EstadoPago.RECHAZADO,
                ReglasPago.inicioVentanaRechazos(ahora));
    }

    private MotivoNoDisponible motivo(ReservaPorCobrar reserva, long rechazos, Instant ahora) {
        return ReglasPago.motivoNoDisponible(reserva.vigente(), reserva.expiraEn(), reserva.espacioVisible(), rechazos,
                ahora, margenCierre);
    }

    /** Resultado de {@link #resumen}. */
    public record ResumenCobro(Long reservaId, boolean intentosAgotados, boolean pagoDisponible) {
    }
}
