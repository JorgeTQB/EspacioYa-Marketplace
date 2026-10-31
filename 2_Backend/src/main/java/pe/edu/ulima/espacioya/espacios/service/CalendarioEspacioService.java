package pe.edu.ulima.espacioya.espacios.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.BloqueoFecha;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;
import pe.edu.ulima.espacioya.espacios.domain.HorarioSemanal;
import pe.edu.ulima.espacioya.espacios.domain.ReglasHorario;
import pe.edu.ulima.espacioya.espacios.dto.BloqueDTO;
import pe.edu.ulima.espacioya.espacios.dto.BloqueoFechaDTO;
import pe.edu.ulima.espacioya.espacios.dto.BloqueosDTO;
import pe.edu.ulima.espacioya.espacios.dto.DisponibilidadDiaDTO;
import pe.edu.ulima.espacioya.espacios.dto.HorarioDTO;
import pe.edu.ulima.espacioya.espacios.repository.BloqueoFechaRepository;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.HorarioSemanalRepository;
import pe.edu.ulima.espacioya.espacios.service.impl.DisponibilidadServiceImpl;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;

/**
 * Horario semanal, fechas bloqueadas y bloques del día de los espacios del arrendador (HU-08). Mismo orden que
 * HU-07 (R39): rol, correo verificado (solo escrituras), propiedad y estado (solo escrituras); luego las reglas
 * de cada operación. Las escrituras releen el espacio con bloqueo, así dos bloqueos de la misma fecha a la vez se
 * serializan y el segundo ve el primero (R40).
 */
@Service
public class CalendarioEspacioService {

    public static final String ACCION_HORARIO = "HORARIO_ESPACIO_ACTUALIZADO";
    public static final String ACCION_BLOQUEO_CREADO = "BLOQUEO_FECHA_CREADO";
    public static final String ACCION_BLOQUEO_ELIMINADO = "BLOQUEO_FECHA_ELIMINADO";
    public static final String CAMPO_FECHA = "fecha";

    public static final String MENSAJE_RANGO_HORAS = "La hora de fin debe ser posterior a la de inicio.";
    public static final String MENSAJE_FRANJAS_SUPERPUESTAS = "Esta franja se cruza con otra del mismo día.";
    public static final String MENSAJE_FECHA_INVALIDA = BloqueoFechaDTO.M_FECHA;
    public static final String MENSAJE_FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";
    public static final String MENSAJE_YA_BLOQUEADA = "Esta fecha ya está bloqueada.";
    public static final String MENSAJE_RESERVA_PAGADA = "No puedes bloquear esta fecha porque tiene reservas pagadas.";
    public static final String MENSAJE_RESERVA_PENDIENTE =
            "Esta fecha tiene una reserva en proceso de pago. Inténtalo de nuevo en unos minutos.";

    private static final String SIN_FRANJAS = "ninguna";

    private final EspacioService espacioService;
    private final CuentaService cuentas;
    private final EspacioRepository espacios;
    private final HorarioSemanalRepository horarios;
    private final BloqueoFechaRepository bloqueos;
    private final DisponibilidadServiceImpl disponibilidad;
    private final AuditoriaService auditoria;

    public CalendarioEspacioService(EspacioService espacioService, CuentaService cuentas, EspacioRepository espacios,
                                    HorarioSemanalRepository horarios, BloqueoFechaRepository bloqueos,
                                    DisponibilidadServiceImpl disponibilidad, AuditoriaService auditoria) {
        this.espacioService = espacioService;
        this.cuentas = cuentas;
        this.espacios = espacios;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.disponibilidad = disponibilidad;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public HorarioDTO consultarHorario(UsuarioAutenticado usuario, Long id) {
        var espacio = propio(usuario, id);
        return HorarioDTO.de(franjasGuardadas(espacio.getId()));
    }

    @Transactional
    public HorarioDTO reemplazarHorario(UsuarioAutenticado usuario, Long id, HorarioDTO horario, String ip) {
        var espacio = habilitadoYEditable(usuario, id);
        var nuevas = horario.aFranjas();
        var problemas = ReglasHorario.revisar(nuevas);
        if (!problemas.isEmpty()) {
            throw new ValidacionException(problemas.stream()
                    .map(p -> new ErrorCampoDTO("franjas[" + p.indice() + "]",
                            p.problema() == ReglasHorario.Problema.RANGO
                                    ? MENSAJE_RANGO_HORAS : MENSAJE_FRANJAS_SUPERPUESTAS))
                    .toList());
        }
        var antes = franjasGuardadas(espacio.getId());
        var despues = nuevas.stream().sorted().toList();
        horarios.borrarDelEspacio(espacio.getId());
        horarios.saveAll(despues.stream().map(franja -> HorarioSemanal.de(espacio.getId(), franja)).toList());
        if (!antes.equals(despues)) {
            auditar(usuario.usuarioId(), ACCION_HORARIO, espacio.getId(), ip,
                    "antes=" + texto(antes) + ";despues=" + texto(despues));
        }
        return HorarioDTO.de(despues);
    }

    @Transactional(readOnly = true)
    public BloqueosDTO listarBloqueos(UsuarioAutenticado usuario, Long id) {
        var espacio = propio(usuario, id);
        var ventana = disponibilidad.ventana();
        var fechas = bloqueos.findByEspacioIdAndFechaGreaterThanEqualOrderByFechaAsc(espacio.getId(), ventana.desde())
                .stream()
                .map(BloqueoFecha::getFecha)
                .toList();
        return new BloqueosDTO(ventana.desde(), ventana.hasta(), fechas);
    }

    @Transactional
    public BloqueoFechaDTO bloquear(UsuarioAutenticado usuario, Long id, BloqueoFechaDTO bloqueo, String ip) {
        var espacio = habilitadoYEditable(usuario, id);
        var fecha = fecha(bloqueo.fecha());
        if (!disponibilidad.ventana().contiene(fecha)) {
            throw new ReglaNegocioException(CAMPO_FECHA, MENSAJE_FUERA_DE_VENTANA);
        }
        if (bloqueos.existsByEspacioIdAndFecha(espacio.getId(), fecha)) {
            throw new ReglaNegocioException(CAMPO_FECHA, MENSAJE_YA_BLOQUEADA);
        }
        var reservas = disponibilidad.reservasDelDia(espacio.getId(), fecha);
        if (reservas.stream().anyMatch(BloqueReservado::pagado)) {
            throw new ReglaNegocioException(MENSAJE_RESERVA_PAGADA);
        }
        if (!reservas.isEmpty()) {
            throw new ReglaNegocioException(MENSAJE_RESERVA_PENDIENTE);
        }
        bloqueos.save(BloqueoFecha.de(espacio.getId(), fecha));
        auditar(usuario.usuarioId(), ACCION_BLOQUEO_CREADO, espacio.getId(), ip, "fecha=" + fecha);
        return new BloqueoFechaDTO(fecha.toString());
    }

    // Sin comprobar la ventana: quitar un bloqueo nunca crea un conflicto.
    @Transactional
    public void desbloquear(UsuarioAutenticado usuario, Long id, String texto, String ip) {
        var espacio = habilitadoYEditable(usuario, id);
        var fecha = fecha(texto);
        var bloqueo = bloqueos.findByEspacioIdAndFecha(espacio.getId(), fecha)
                .orElseThrow(RecursoNoEncontradoException::new);
        bloqueos.delete(bloqueo);
        auditar(usuario.usuarioId(), ACCION_BLOQUEO_ELIMINADO, espacio.getId(), ip, "fecha=" + fecha);
    }

    @Transactional(readOnly = true)
    public DisponibilidadDiaDTO disponibilidad(UsuarioAutenticado usuario, Long id, String texto) {
        var espacio = propio(usuario, id);
        var fecha = fecha(texto);
        if (!disponibilidad.ventana().contiene(fecha)) {
            throw new ReglaNegocioException(CAMPO_FECHA, MENSAJE_FUERA_DE_VENTANA);
        }
        var bloques = disponibilidad.bloquesDelDia(espacio.getId(), fecha).stream().map(BloqueDTO::de).toList();
        return new DisponibilidadDiaDTO(fecha, bloqueos.existsByEspacioIdAndFecha(espacio.getId(), fecha), bloques);
    }

    private Espacio habilitadoYEditable(UsuarioAutenticado usuario, Long id) {
        var arrendadorId = espacioService.arrendadorHabilitado(usuario);
        var espacio = espacios.findParaActualizar(id, arrendadorId).orElseThrow(RecursoNoEncontradoException::new);
        if (!espacio.esEditable()) {
            throw new ReglaNegocioException(EspacioService.MENSAJE_ESTADO_NO_EDITABLE);
        }
        return espacio;
    }

    private Espacio propio(UsuarioAutenticado usuario, Long id) {
        cuentas.habilitacionArrendador(usuario);
        return espacios.findByIdAndArrendadorId(id, usuario.usuarioId()).orElseThrow(RecursoNoEncontradoException::new);
    }

    // ISO_LOCAL_DATE es estricto: 2026-02-30 no es una fecha (R12).
    private static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaNegocioException(CAMPO_FECHA, MENSAJE_FECHA_INVALIDA);
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(CAMPO_FECHA, MENSAJE_FECHA_INVALIDA);
        }
    }

    private List<FranjaHoraria> franjasGuardadas(Long espacioId) {
        return horarios.findByEspacioIdOrderByDiaSemanaAscHoraInicioAsc(espacioId).stream()
                .map(HorarioSemanal::aFranja)
                .sorted()
                .toList();
    }

    private static String texto(List<FranjaHoraria> franjas) {
        if (franjas.isEmpty()) {
            return SIN_FRANJAS;
        }
        return franjas.stream().map(FranjaHoraria::aTexto).collect(Collectors.joining(","));
    }

    private void auditar(Long usuarioId, String accion, Long espacioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, EspacioService.ENTIDAD_ESPACIO, espacioId, ip,
                detalle));
    }
}
