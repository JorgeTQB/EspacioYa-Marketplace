package pe.edu.ulima.espacioya.reservas.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioBloqueado;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.reservas.domain.CalculoReembolso;
import pe.edu.ulima.espacioya.reservas.domain.MotivoNoCancelable;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.ReembolsoCalculado;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.domain.SolicitudCancelacion;
import pe.edu.ulima.espacioya.reservas.dto.CancelacionDTO;
import pe.edu.ulima.espacioya.reservas.dto.ConfirmarCancelacionDTO;
import pe.edu.ulima.espacioya.reservas.dto.VistaCancelacionDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

/**
 * Cancelación de reservas (HU-15), Client del Strategy (★): arma la {@link SolicitudCancelacion} solo con datos del
 * servidor y deja que {@link CalculoReembolso} elija la política; no conoce las estrategias ni decide la regla. Quien
 * cancela lo fija la ruta (D1). La confirmación toma {@code espacio → reserva} como HU-12 y HU-13 (R33).
 */
@Service
public class CancelacionReservaService {

    public static final String ACCION_RESERVA_CANCELADA = "RESERVA_CANCELADA";
    public static final String MENSAJE_YA_CANCELADA = "Esta reserva ya está cancelada.";
    public static final String MENSAJE_NO_CONFIRMADA = "Solo se puede cancelar una reserva confirmada.";
    public static final String MENSAJE_YA_INICIADA = "Esta reserva ya empezó o terminó: no se puede cancelar.";
    public static final String MENSAJE_MONTO_VISTO = "Indica el monto a reembolsar que se te mostró.";
    public static final String MENSAJE_MONTO_CAMBIO = "El monto a reembolsar cambió. Revísalo antes de confirmar.";
    public static final String CAMPO_MONTO_VISTO = "montoReembolsoVisto";

    public static final String ENTIDAD_RESERVA = "reserva";
    private static final int DECIMALES_MONTO = 2;

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final DisponibilidadService disponibilidad;
    private final CatalogoEspaciosService catalogo;
    private final PagoService pagos;
    private final AvisosCancelacion avisos;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final CalculoReembolso calculo = CalculoReembolso.politicaFija();

    public CancelacionReservaService(ReservaRepository reservas, ReservaBloqueRepository bloques,
                                     DisponibilidadService disponibilidad, CatalogoEspaciosService catalogo,
                                     PagoService pagos, AvisosCancelacion avisos, AuditoriaService auditoria,
                                     Clock reloj) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.disponibilidad = disponibilidad;
        this.catalogo = catalogo;
        this.pagos = pagos;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Lo que se reembolsaría si se cancela ahora (CA2). Sin bloqueos ni escrituras (R18): la confirmación recalcula.
     *
     * @throws AccesoDenegadoException si el rol no es el de la ruta, sin leer nada (R2, R3).
     * @throws RecursoNoEncontradoException si el id está mal escrito, no existe o no es propia (R4–R6).
     * @throws ReglaNegocioException si la reserva no es cancelable (R8–R10).
     */
    @Transactional(readOnly = true)
    public VistaCancelacionDTO vistaPrevia(UsuarioAutenticado usuario, String id, QuienCancela quien) {
        exigirRol(usuario, quien);
        var reservaId = idReserva(id);
        var propia = switch (quien) {
            case USUARIO -> deUsuario(usuario, reservaId);
            case ARRENDADOR -> deArrendador(usuario, reservaId);
            case ADMIN -> throw new AccesoDenegadoException();
        };
        var reserva = propia.reserva();
        var horario = horario(reserva);
        var ahora = Instant.now(reloj);
        var inicio = horario.instanteDeInicio();
        exigirCancelable(reserva, inicio, ahora);
        var reembolso = calculo.calcular(new SolicitudCancelacion(quien, inicio, ahora, reserva.getMontoTotal()));
        return new VistaCancelacionDTO(reserva.getId(), propia.espacioNombre(), horario.fecha(), horario.horaInicio(),
                horario.horaFin(), reserva.getMontoTotal(), reembolso.politica(), reembolso.porcentaje(),
                reembolso.monto());
    }

    /**
     * Cancela la reserva (CA3): estado, bloques, reembolso si es mayor que 0, auditoría y correos después del commit.
     * Cualquier excepción revierte todo (R36).
     *
     * @throws AccesoDenegadoException si el rol no es el de la ruta, sin leer nada (R2, R3).
     * @throws RecursoNoEncontradoException si el id está mal escrito, no existe o no es propia (R4–R6).
     * @throws ReglaNegocioException si falta o está mal el monto visto (R31), si la reserva no es cancelable
     *         (R8–R10, R34) o si el monto visto no es el recalculado (R32).
     */
    @Transactional
    public CancelacionDTO confirmar(UsuarioAutenticado usuario, String id, QuienCancela quien,
                                    ConfirmarCancelacionDTO datos, String ip) {
        exigirRol(usuario, quien);
        var reservaId = idReserva(id);
        var visto = montoVisto(datos);
        var espacioId = reservas.findEspacioIdById(reservaId).orElseThrow(RecursoNoEncontradoException::new);
        var espacio = disponibilidad.bloquearEspacio(espacioId);
        var reserva = reservas.findParaActualizar(reservaId)
                .orElseThrow(() -> new IllegalStateException("La reserva no puede desaparecer: no se borran"));
        if (!esPropia(usuario, quien, reserva, espacio)) {
            throw new RecursoNoEncontradoException();
        }
        var horario = horario(reserva);
        // Después de los dos bloqueos: una confirmación simultánea ya habrá dejado la reserva CANCELADA (R34).
        var ahora = Instant.now(reloj);
        var inicio = horario.instanteDeInicio();
        exigirCancelable(reserva, inicio, ahora);
        var reembolso = calculo.calcular(new SolicitudCancelacion(quien, inicio, ahora, reserva.getMontoTotal()));
        if (visto.compareTo(reembolso.monto()) != 0) {
            throw new ReglaNegocioException(CAMPO_MONTO_VISTO, MENSAJE_MONTO_CAMBIO);
        }
        reserva.cancelar(quien, inicio, reembolso.monto(), ahora);
        bloques.desactivarDeReserva(reservaId);
        // Con 0 % no hay nada que devolver (D6).
        if (reembolso.monto().signum() > 0) {
            pagos.reembolsarCancelacion(new ReembolsoPorCancelacion(reservaId, reembolso.monto(),
                    usuario.usuarioId(), ip));
        }
        auditar(usuario, reserva, quien, reembolso, ip);
        avisos.programar(reserva.getUsuarioId(), espacio.arrendadorId(), espacio.nombre(), horario, quien, reembolso);
        return new CancelacionDTO(reserva.getId(), reserva.getEstado().name(), quien.name(), ahora,
                reembolso.politica(), reembolso.porcentaje(), reembolso.monto());
    }

    private Propia deUsuario(UsuarioAutenticado usuario, Long reservaId) {
        var reserva = reservas.findByIdAndUsuarioId(reservaId, usuario.usuarioId())
                .orElseThrow(RecursoNoEncontradoException::new);
        var nombre = catalogo.nombres(List.of(reserva.getEspacioId())).get(reserva.getEspacioId());
        return new Propia(reserva, nombre);
    }

    private Propia deArrendador(UsuarioAutenticado usuario, Long reservaId) {
        var reserva = reservas.findById(reservaId).orElseThrow(RecursoNoEncontradoException::new);
        var espacios = catalogo.nombresDelArrendador(usuario.usuarioId());
        if (!espacios.containsKey(reserva.getEspacioId())) {
            throw new RecursoNoEncontradoException();
        }
        return new Propia(reserva, espacios.get(reserva.getEspacioId()));
    }

    // La ruta solo decide de quién debe ser la reserva (RNF-SEG-19); el reembolso lo decide el Strategy.
    private static boolean esPropia(UsuarioAutenticado usuario, QuienCancela quien, Reserva reserva,
                                    EspacioBloqueado espacio) {
        return switch (quien) {
            case USUARIO -> usuario.usuarioId().equals(reserva.getUsuarioId());
            case ARRENDADOR -> usuario.usuarioId().equals(espacio.arrendadorId());
            case ADMIN -> throw new AccesoDenegadoException();
        };
    }

    private ReservaConHorario horario(Reserva reserva) {
        var filas = bloques.findByReservaIdOrderByHoraInicioAsc(reserva.getId());
        return new ReservaConHorario(reserva, filas.getFirst().getFecha(), filas.getFirst().getHoraInicio(),
                filas.getLast().getHoraInicio() + 1);
    }

    private static void exigirCancelable(Reserva reserva, Instant inicio, Instant ahora) {
        reserva.motivoNoCancelable(inicio, ahora).ifPresent(motivo -> {
            throw new ReglaNegocioException(mensaje(motivo));
        });
    }

    private static String mensaje(MotivoNoCancelable motivo) {
        return switch (motivo) {
            case YA_CANCELADA -> MENSAJE_YA_CANCELADA;
            case NO_CONFIRMADA -> MENSAJE_NO_CONFIRMADA;
            case YA_INICIADA -> MENSAJE_YA_INICIADA;
        };
    }

    private void auditar(UsuarioAutenticado usuario, Reserva reserva, QuienCancela quien,
                         ReembolsoCalculado reembolso, String ip) {
        var detalle = "canceladaPor=" + quien.name() + ";espacioId=" + reserva.getEspacioId() + ";politica="
                + reembolso.politica() + ";porcentaje=" + reembolso.porcentaje() + ";monto="
                + reembolso.monto().toPlainString();
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_RESERVA_CANCELADA, ENTIDAD_RESERVA,
                reserva.getId(), ip, detalle));
    }

    // HU-27 R77: el admin solo cancela al ocultar un espacio por fraude, nunca por las rutas de HU-15.
    private static void exigirRol(UsuarioAutenticado usuario, QuienCancela quien) {
        if (quien == QuienCancela.ADMIN || usuario == null || !quien.rolRequerido().equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private static Long idReserva(String id) {
        return ConversionReservas.idReserva(id).orElseThrow(RecursoNoEncontradoException::new);
    }

    // Falta, negativo o con más de 2 decimales (161.00 y 161 son el mismo monto) → 400 en el campo (R31).
    private static BigDecimal montoVisto(ConfirmarCancelacionDTO datos) {
        var monto = datos == null ? null : datos.montoReembolsoVisto();
        if (monto == null || monto.signum() < 0 || monto.stripTrailingZeros().scale() > DECIMALES_MONTO) {
            throw new ReglaNegocioException(CAMPO_MONTO_VISTO, MENSAJE_MONTO_VISTO);
        }
        return monto;
    }

    private record Propia(Reserva reserva, String espacioNombre) {
    }
}
