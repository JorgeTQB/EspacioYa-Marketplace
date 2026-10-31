package pe.edu.ulima.espacioya.reservas.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioRetenido;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaBloque;
import pe.edu.ulima.espacioya.reservas.domain.SeleccionBloques;
import pe.edu.ulima.espacioya.reservas.dto.CrearReservaDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

/**
 * Crea y consulta reservas (HU-12). Orden fijo de bloqueos, {@code usuario → espacio → reserva} (DT-1): el del usuario
 * serializa su cupo y el del espacio serializa a dos usuarios y a la reserva con el bloqueo de fechas.
 */
@Service
public class ReservaServiceImpl implements ReservaService {

    public static final String ACCION_RESERVA_CREADA = "RESERVA_CREADA";
    public static final String MENSAJE_CUPO =
            "Ya tienes 3 reservas pendientes de pago. Paga una o espera a que expire para reservar otra.";
    public static final String MENSAJE_FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";

    private static final int MAXIMO_PENDIENTES = 3;
    private static final String ROL_QUE_RESERVA = "USUARIO";
    private static final String CAMPO_FECHA = "fecha";
    private static final String CAMPO_HORAS = "horas";
    private static final String ENTIDAD_RESERVA = "reserva";
    private static final String RESTRICCION_BLOQUE_ACTIVO = "uq_reserva_bloque_activo";
    private static final Pattern ID_BIEN_ESCRITO = Pattern.compile("[0-9]{1,19}");

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final DisponibilidadService disponibilidad;
    private final CatalogoEspaciosService catalogo;
    private final CuentaService cuentas;
    private final ExpiracionReservas expiracion;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public ReservaServiceImpl(ReservaRepository reservas, ReservaBloqueRepository bloques,
                              DisponibilidadService disponibilidad, CatalogoEspaciosService catalogo,
                              CuentaService cuentas, ExpiracionReservas expiracion, AuditoriaService auditoria,
                              TransactionTemplate transacciones, Clock reloj) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.disponibilidad = disponibilidad;
        this.catalogo = catalogo;
        this.cuentas = cuentas;
        this.expiracion = expiracion;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // Sin @Transactional: con él, la violación de la restricción llegaría al confirmar y no se podría traducir (DT-7).
    @Override
    public ReservaDTO crear(UsuarioAutenticado usuario, CrearReservaDTO datos, String ip) {
        if (!ROL_QUE_RESERVA.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
        var fecha = fechaEnVentana(datos.fecha());
        if (!SeleccionBloques.esValida(datos.horas())) {
            throw new ReglaNegocioException(CAMPO_HORAS, CrearReservaDTO.M_HORAS);
        }
        var horas = SeleccionBloques.ordenadas(datos.horas());
        try {
            return transacciones.execute(estado -> crearEnTransaccion(usuario, datos.espacioId(), fecha, horas, ip));
        } catch (DataIntegrityViolationException conflicto) {
            if (esBloqueYaRetenido(conflicto)) {
                throw new ReglaNegocioException(DisponibilidadService.MENSAJE_HORARIO_NO_DISPONIBLE);
            }
            throw conflicto;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaDTO consultar(UsuarioAutenticado usuario, String id) {
        var reservaId = idValido(id);
        if (reservaId == null) {
            throw new RecursoNoEncontradoException();
        }
        var reserva = reservas.findByIdAndUsuarioId(reservaId, usuario.usuarioId())
                .orElseThrow(RecursoNoEncontradoException::new);
        var filas = bloques.findByReservaIdOrderByHoraInicioAsc(reserva.getId());
        var nombre = catalogo.nombres(List.of(reserva.getEspacioId())).get(reserva.getEspacioId());
        return aDto(reserva, nombre, filas.getFirst().getFecha(), filas.getFirst().getHoraInicio(),
                filas.getLast().getHoraInicio() + 1);
    }

    private ReservaDTO crearEnTransaccion(UsuarioAutenticado usuario, Long espacioId, LocalDate fecha,
                                          List<Integer> horas, String ip) {
        cuentas.bloquearCuenta(usuario.usuarioId());
        if (reservas.contarVigentes(usuario.usuarioId(), Instant.now(reloj)) >= MAXIMO_PENDIENTES) {
            throw new ReglaNegocioException(MENSAJE_CUPO);
        }
        var retenido = disponibilidad.retenerSiLibres(espacioId, fecha, horas);
        // Después de retener: si fuera antes, una reserva que vence mientras se espera el bloqueo del espacio sería
        // libre para el puerto pero no vencida para liberarVencidas, y chocaría con el índice (falso conflicto).
        var ahora = Instant.now(reloj);
        expiracion.liberarVencidas(espacioId, fecha, horas, ahora);
        var reserva = reservas.save(
                Reserva.pendiente(usuario.usuarioId(), espacioId, retenido.precioHora(), horas.size(), ahora));
        bloques.saveAllAndFlush(horas.stream()
                .map(hora -> new ReservaBloque(reserva.getId(), espacioId, fecha, hora))
                .toList());
        auditar(usuario, reserva, fecha, horas, ip);
        return aDto(reserva, retenido, fecha, horas);
    }

    private void auditar(UsuarioAutenticado usuario, Reserva reserva, LocalDate fecha, List<Integer> horas, String ip) {
        var lista = horas.stream().map(String::valueOf).collect(Collectors.joining(","));
        var detalle = "espacioId=" + reserva.getEspacioId() + ";fecha=" + fecha + ";horas=" + lista + ";monto="
                + reserva.getMontoTotal().toPlainString();
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_RESERVA_CREADA, ENTIDAD_RESERVA,
                reserva.getId(), ip, detalle));
    }

    private ReservaDTO aDto(Reserva reserva, EspacioRetenido retenido, LocalDate fecha, List<Integer> horas) {
        return aDto(reserva, retenido.nombre(), fecha, horas.getFirst(), horas.getLast() + 1);
    }

    private ReservaDTO aDto(Reserva reserva, String nombre, LocalDate fecha, int horaInicio, int horaFin) {
        return ConversionReservas.aDto(reserva, nombre, fecha, horaInicio, horaFin, Instant.now(reloj));
    }

    private LocalDate fechaEnVentana(String texto) {
        var fecha = fecha(texto);
        if (!VentanaReserva.desde(ZonaHoraria.hoy(reloj)).contiene(fecha)) {
            throw new ReglaNegocioException(CAMPO_FECHA, MENSAJE_FUERA_DE_VENTANA);
        }
        return fecha;
    }

    // ISO_LOCAL_DATE es estricto: 2026-02-30 no es una fecha.
    private static LocalDate fecha(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(CAMPO_FECHA, CrearReservaDTO.M_FECHA);
        }
    }

    // Por el nombre de la restricción: otro fallo de integridad no debe disfrazarse de conflicto de horario (R23).
    private static boolean esBloqueYaRetenido(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(RESTRICCION_BLOQUE_ACTIVO);
    }

    // null = no es un id bien escrito: 1 a 19 dígitos, sin desbordar un long y mayor que 0.
    private static Long idValido(String texto) {
        if (texto == null || !ID_BIEN_ESCRITO.matcher(texto).matches()) {
            return null;
        }
        try {
            var id = Long.parseLong(texto);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
