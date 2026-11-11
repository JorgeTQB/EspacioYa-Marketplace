package pe.edu.ulima.espacioya.reservas.service.impl;

import static pe.edu.ulima.espacioya.comun.seguridad.RolesSesion.exigirAdmin;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.reservas.domain.CalculoReembolso;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.ReembolsoCalculado;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.domain.SolicitudCancelacion;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;
import pe.edu.ulima.espacioya.reservas.service.AvisosCancelacion;
import pe.edu.ulima.espacioya.reservas.service.CancelacionPorModeracion;
import pe.edu.ulima.espacioya.reservas.service.CancelacionReservaService;
import pe.edu.ulima.espacioya.reservas.service.ReservasCanceladasPorModeracion;

/**
 * Cancelación de las reservas por iniciar de un espacio ocultado por fraude (HU-27 P9, P17). Otro Client del Strategy
 * (★ HU-15): arma la solicitud con {@code QuienCancela.ADMIN} y {@link CalculoReembolso} elige la política
 * {@code MODERACION}. Toma {@code espacio → reserva → pago} como HU-15, así una cancelación simultánea del usuario se
 * serializa y solo una de las dos reembolsa (R73).
 */
@Service
public class CancelacionPorModeracionImpl implements CancelacionPorModeracion {

    private static final QuienCancela QUIEN = QuienCancela.ADMIN;

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final DisponibilidadService disponibilidad;
    private final PagoService pagos;
    private final AvisosCancelacion avisos;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final CalculoReembolso calculo = CalculoReembolso.politicaFija();

    public CancelacionPorModeracionImpl(ReservaRepository reservas, ReservaBloqueRepository bloques,
                                        DisponibilidadService disponibilidad, PagoService pagos,
                                        AvisosCancelacion avisos, AuditoriaService auditoria, Clock reloj) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.disponibilidad = disponibilidad;
        this.pagos = pagos;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ReservasCanceladasPorModeracion cancelarPorFraude(UsuarioAutenticado admin, Long espacioId, String ip) {
        exigirAdmin(admin);
        // El llamador ya tiene el bloqueo del espacio (lo ocultó): esta lectura no espera y fija el orden de HU-15.
        var espacio = disponibilidad.bloquearEspacio(espacioId);
        var confirmadas = reservas.findConfirmadasParaActualizar(espacioId);
        var ahora = Instant.now(reloj);
        var canceladas = 0;
        for (var reserva : confirmadas) {
            var horario = horario(reserva);
            var inicio = horario.instanteDeInicio();
            // R65: las ya iniciadas o terminadas no se cancelan; su vía es el reclamo de HU-26.
            if (reserva.motivoNoCancelable(inicio, ahora).isPresent()) {
                continue;
            }
            var reembolso = calculo.calcular(new SolicitudCancelacion(QUIEN, inicio, ahora, reserva.getMontoTotal()));
            reserva.cancelar(QUIEN, inicio, reembolso.monto(), ahora);
            bloques.desactivarDeReserva(reserva.getId());
            pagos.reembolsarCancelacion(new ReembolsoPorCancelacion(reserva.getId(), reembolso.monto(),
                    admin.usuarioId(), ip));
            auditar(admin, reserva, reembolso, ip);
            avisos.programarAlUsuario(reserva.getUsuarioId(), espacio.nombre(), horario, QUIEN, reembolso);
            canceladas++;
        }
        return new ReservasCanceladasPorModeracion(canceladas);
    }

    private ReservaConHorario horario(Reserva reserva) {
        var filas = bloques.findByReservaIdOrderByHoraInicioAsc(reserva.getId());
        return new ReservaConHorario(reserva, filas.getFirst().getFecha(), filas.getFirst().getHoraInicio(),
                filas.getLast().getHoraInicio() + 1);
    }

    private void auditar(UsuarioAutenticado admin, Reserva reserva, ReembolsoCalculado reembolso, String ip) {
        var detalle = "canceladaPor=" + QUIEN.name() + ";espacioId=" + reserva.getEspacioId() + ";politica="
                + reembolso.politica() + ";porcentaje=" + reembolso.porcentaje() + ";monto="
                + reembolso.monto().toPlainString();
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), CancelacionReservaService.ACCION_RESERVA_CANCELADA,
                CancelacionReservaService.ENTIDAD_RESERVA, reserva.getId(), ip, detalle));
    }
}
