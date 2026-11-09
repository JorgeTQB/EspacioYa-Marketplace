package pe.edu.ulima.espacioya.administracion.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.administracion.domain.MensajeReclamo;
import pe.edu.ulima.espacioya.administracion.domain.MotivoNoReclamable;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;
import pe.edu.ulima.espacioya.administracion.domain.ReglasReclamo;
import pe.edu.ulima.espacioya.administracion.dto.CrearReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.MensajeReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.NuevoMensajeDTO;
import pe.edu.ulima.espacioya.administracion.dto.PaginaReclamosDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReclamablesDTO;
import pe.edu.ulima.espacioya.administracion.repository.MensajeReclamoRepository;
import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosReclamoAbierto;
import pe.edu.ulima.espacioya.busqueda.service.BusquedaService;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.texto.SanitizadorTexto;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

/**
 * Reclamos de las partes (HU-26 CA1, RF-F-07, RN-12): el usuario abre un reclamo sobre su reserva y las dos partes
 * escriben en su hilo mientras está abierto. Clase concreta: nadie fuera de {@code administracion} la usa.
 */
@Service
public class ReclamoService {

    public static final String ACCION_ABIERTO = "RECLAMO_ABIERTO";
    public static final String ACCION_MENSAJE = "RECLAMO_MENSAJE";
    public static final int TAMANO_PAGINA = 10;

    public static final String MENSAJE_NO_RECLAMABLE = "Solo puedes reclamar una reserva confirmada que ya empezó.";
    public static final String MENSAJE_PLAZO_VENCIDO =
            "El plazo para reclamar terminó: es de 48 horas desde el fin de la reserva.";
    public static final String MENSAJE_YA_RECLAMADA = "Ya abriste un reclamo para esta reserva.";
    public static final String MENSAJE_RECLAMO_CERRADO = "Este reclamo ya se resolvió y no admite mensajes.";
    public static final String MENSAJE_LIMITE_MENSAJES = "Llegaste al máximo de 10 mensajes en este reclamo.";

    static final String ENTIDAD_RECLAMO = "reclamo";
    private static final String CAMPO_DESCRIPCION = "descripcion";
    private static final String CAMPO_TEXTO = "texto";
    private static final String ROL_USUARIO = "USUARIO";
    private static final String ROL_ARRENDADOR = "ARRENDADOR";
    private static final String RESTRICCION_UNO_POR_RESERVA = "uq_reclamo_reserva";
    private static final Sort MAS_NUEVOS_PRIMERO = Sort.by(Sort.Direction.DESC, "abiertoEn", "id");

    private final ReclamoRepository reclamos;
    private final MensajeReclamoRepository mensajes;
    private final ReservasParaReclamo reservas;
    private final PagoService pagos;
    private final CuentaService cuentas;
    private final VistaReclamos vista;
    private final AvisosReclamo avisos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public ReclamoService(ReclamoRepository reclamos, MensajeReclamoRepository mensajes,
                          ReservasParaReclamo reservas, PagoService pagos, CuentaService cuentas, VistaReclamos vista,
                          AvisosReclamo avisos, AuditoriaService auditoria, TransactionTemplate transacciones,
                          Clock reloj) {
        this.reclamos = reclamos;
        this.mensajes = mensajes;
        this.reservas = reservas;
        this.pagos = pagos;
        this.cuentas = cuentas;
        this.vista = vista;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // Sin @Transactional: con él, la violación de uq_reclamo_reserva llegaría al confirmar y no se traduciría (DT-7).
    public DetalleReclamoDTO abrir(UsuarioAutenticado usuario, CrearReclamoDTO datos, String ip) {
        exigirUsuario(usuario);
        var descripcion = SanitizadorTexto.limpiar(datos.descripcion());
        if (descripcion.isEmpty()) {
            throw new ReglaNegocioException(CAMPO_DESCRIPCION, CrearReclamoDTO.M_DESCRIPCION);
        }
        try {
            return transacciones.execute(estado -> abrirEnTransaccion(usuario, datos, descripcion, ip));
        } catch (DataIntegrityViolationException conflicto) {
            if (esReservaYaReclamada(conflicto)) {
                throw new ReglaNegocioException(MENSAJE_YA_RECLAMADA);
            }
            throw conflicto;
        }
    }

    @Transactional(readOnly = true)
    public ReclamablesDTO reclamables(UsuarioAutenticado usuario) {
        exigirUsuario(usuario);
        var confirmadas = reservas.confirmadasDe(usuario.usuarioId());
        if (confirmadas.isEmpty()) {
            return new ReclamablesDTO(List.of());
        }
        var ahora = Instant.now(reloj);
        var conReclamo = reclamos.reservasConReclamo(confirmadas.stream().map(ReservaParaReclamo::id).toList());
        var ids = confirmadas.stream()
                .filter(reserva -> !conReclamo.contains(reserva.id()))
                .filter(reserva -> ReglasReclamo.motivoNoReclamable(reserva.confirmada(), reserva.inicioEn(),
                        reserva.finEn(), ahora).isEmpty())
                .map(ReservaParaReclamo::id)
                .filter(id -> !pagos.reservaLiquidada(id))
                .sorted(Comparator.naturalOrder())
                .toList();
        return new ReclamablesDTO(ids);
    }

    @Transactional(readOnly = true)
    public PaginaReclamosDTO listar(UsuarioAutenticado parte, String pagina) {
        var esUsuario = exigirParte(parte);
        var numero = VistaReclamos.numeroDePagina(pagina);
        if (numero == 0) {
            throw new ReglaNegocioException(BusquedaService.CAMPO_PAGINA, BusquedaService.MENSAJE_PAGINA_INVALIDA);
        }
        var solicitud = PageRequest.of(numero - 1, TAMANO_PAGINA, MAS_NUEVOS_PRIMERO);
        // Spring Data rechaza un offset mayor que un int, pero esa página es válida: no tiene nada (HU-24 R34).
        if (solicitud.getOffset() > Integer.MAX_VALUE) {
            return new PaginaReclamosDTO(numero, List.of(), false);
        }
        var trozo = esUsuario ? reclamos.findByUsuarioId(parte.usuarioId(), solicitud)
                : reclamos.findByArrendadorId(parte.usuarioId(), solicitud);
        return new PaginaReclamosDTO(numero, vista.resumenes(trozo.getContent()), trozo.hasNext());
    }

    @Transactional(readOnly = true)
    public DetalleReclamoDTO detalle(UsuarioAutenticado parte, Long reclamoId) {
        exigirParte(parte);
        var reclamo = reclamos.findById(reclamoId).orElseThrow(RecursoNoEncontradoException::new);
        var papel = reclamo.papelDe(parte.usuarioId()).orElseThrow(RecursoNoEncontradoException::new);
        return vista.paraParte(reclamo, papel, parte.usuarioId());
    }

    @Transactional
    public MensajeReclamoDTO enviarMensaje(UsuarioAutenticado parte, Long reclamoId, NuevoMensajeDTO datos,
                                           String ip) {
        exigirParte(parte);
        var texto = SanitizadorTexto.limpiar(datos.texto());
        if (texto.isEmpty()) {
            throw new ReglaNegocioException(CAMPO_TEXTO, NuevoMensajeDTO.M_TEXTO);
        }
        // Bajo el bloqueo de la fila: dos envíos o un envío y la resolución se serializan (R29, DT-8).
        var reclamo = reclamos.findParaActualizar(reclamoId).orElseThrow(RecursoNoEncontradoException::new);
        var papel = reclamo.papelDe(parte.usuarioId()).orElseThrow(RecursoNoEncontradoException::new);
        if (!reclamo.estaAbierto()) {
            throw new ReglaNegocioException(MENSAJE_RECLAMO_CERRADO);
        }
        if (mensajes.countByReclamoIdAndAutorId(reclamo.getId(), parte.usuarioId())
                >= ReglasReclamo.MAXIMO_MENSAJES_POR_PARTE) {
            throw new ReglaNegocioException(MENSAJE_LIMITE_MENSAJES);
        }
        var mensaje = mensajes.saveAndFlush(MensajeReclamo.nuevo(reclamo.getId(), parte.usuarioId(), papel, texto,
                Instant.now(reloj)));
        auditoria.registrar(new EventoAuditoria(parte.usuarioId(), ACCION_MENSAJE, ENTIDAD_RECLAMO, reclamo.getId(),
                ip, "mensajeId=" + mensaje.getId() + ";papel=" + papel.name()));
        return VistaReclamos.aDto(mensaje);
    }

    private DetalleReclamoDTO abrirEnTransaccion(UsuarioAutenticado usuario, CrearReclamoDTO datos,
                                                 String descripcion, String ip) {
        var reserva = reservas.delUsuario(usuario.usuarioId(), datos.reservaId());
        // Antes que la ventana: si ya reclamó, esa es la causa real (R12).
        if (reclamos.existsByReservaId(reserva.id())) {
            throw new ReglaNegocioException(MENSAJE_YA_RECLAMADA);
        }
        // DT-6, R21: el bloqueo antes de leer el reloj y liquidacion_detalle; o la generación ve este reclamo, o su
        // corte ya pasó fin + 48 h y este "ahora", tomado después, cae fuera de la ventana.
        pagos.bloquearGeneracionLiquidaciones();
        var ahora = Instant.now(reloj);
        ReglasReclamo.motivoNoReclamable(reserva.confirmada(), reserva.inicioEn(), reserva.finEn(), ahora)
                .ifPresent(motivo -> {
                    throw new ReglaNegocioException(mensajeDe(motivo));
                });
        if (pagos.reservaLiquidada(reserva.id())) {
            throw new ReglaNegocioException(MENSAJE_PLAZO_VENCIDO);
        }
        var motivo = MotivoReclamo.valueOf(datos.motivo());
        var reclamo = reclamos.saveAndFlush(Reclamo.abrir(reserva.id(), usuario.usuarioId(), reserva.arrendadorId(),
                motivo, descripcion, ahora));
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_ABIERTO, ENTIDAD_RECLAMO, reclamo.getId(),
                ip, "reservaId=" + reserva.id() + ";motivo=" + motivo.name()));
        avisos.reclamoAbierto(cuentas.correoDe(reserva.arrendadorId()), new DatosReclamoAbierto(
                reserva.espacioNombre(), reserva.fecha(), reserva.horaInicio(), reserva.horaFin(), motivo));
        return vista.paraParte(reclamo, reserva, PapelReclamo.USUARIO, usuario.usuarioId());
    }

    // Antes de leer nada: R10 pide el 403 sin tocar las reservas.
    private static void exigirUsuario(UsuarioAutenticado usuario) {
        if (!ROL_USUARIO.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    // R26: el admin no es parte de ningún reclamo. Devuelve si la sesión es un USUARIO.
    private static boolean exigirParte(UsuarioAutenticado parte) {
        if (ROL_USUARIO.equals(parte.rol())) {
            return true;
        }
        if (ROL_ARRENDADOR.equals(parte.rol())) {
            return false;
        }
        throw new AccesoDenegadoException();
    }

    private static String mensajeDe(MotivoNoReclamable motivo) {
        return switch (motivo) {
            case NO_RECLAMABLE -> MENSAJE_NO_RECLAMABLE;
            case PLAZO_VENCIDO -> MENSAJE_PLAZO_VENCIDO;
        };
    }

    // Por el nombre de la restricción: otro fallo de integridad no debe disfrazarse de reclamo repetido (R14).
    private static boolean esReservaYaReclamada(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(RESTRICCION_UNO_POR_RESERVA);
    }
}
