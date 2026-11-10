package pe.edu.ulima.espacioya.administracion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.domain.EstadoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.domain.TipoReembolsoReclamo;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReclamoAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.PaginaReclamosDTO;
import pe.edu.ulima.espacioya.administracion.dto.ResolucionReclamoDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosResolucion;
import pe.edu.ulima.espacioya.busqueda.service.BusquedaService;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.texto.SanitizadorTexto;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorReclamo;

/**
 * Panel del admin para los reclamos (HU-26 CA2, CA3, RF-F-08, RF-F-09). El rol se comprueba antes de leer. Resolver
 * bloquea la fila de {@code reclamo} (DT-8); a favor del usuario pide el reembolso con el Command de HU-16 y los
 * correos salen tras el commit.
 */
@Service
public class ReclamoAdminService {

    public static final String ACCION_RESUELTO = "RECLAMO_RESUELTO";
    public static final int TAMANO_PAGINA = 10;

    public static final String MENSAJE_REEMBOLSO = "Elige reembolso total o parcial.";
    public static final String MENSAJE_MONTO =
            "Escribe un monto mayor que 0 y menor que lo pagado, con hasta 2 decimales.";
    public static final String MENSAJE_GRUPO = "Elige reclamos abiertos o resueltos.";
    public static final String MENSAJE_YA_RESUELTO = "Este reclamo ya se resolvió.";
    public static final String GRUPO_ABIERTOS = "abiertos";
    public static final String GRUPO_RESUELTOS = "resueltos";

    private static final String ROL_ADMIN = "ADMIN";
    private static final String CAMPO_GRUPO = "grupo";
    private static final String CAMPO_REEMBOLSO = "reembolso";
    private static final String CAMPO_MONTO = "montoReembolso";
    private static final String CAMPO_RESOLUCION = "resolucion";
    private static final Sort MAS_ANTIGUOS_PRIMERO = Sort.by(Sort.Direction.ASC, "abiertoEn", "id");
    private static final Sort RESUELTOS_RECIENTES = Sort.by(Sort.Direction.DESC, "resueltoEn", "id");
    private static final List<EstadoReclamo> RESUELTOS =
            List.of(EstadoReclamo.RESUELTO_USUARIO, EstadoReclamo.RESUELTO_ARRENDADOR);

    private final ReclamoRepository reclamos;
    private final PagoService pagos;
    private final CuentaService cuentas;
    private final VistaReclamos vista;
    private final AvisosReclamo avisos;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public ReclamoAdminService(ReclamoRepository reclamos, PagoService pagos, CuentaService cuentas,
                               VistaReclamos vista, AvisosReclamo avisos, AuditoriaService auditoria, Clock reloj) {
        this.reclamos = reclamos;
        this.pagos = pagos;
        this.cuentas = cuentas;
        this.vista = vista;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PaginaReclamosDTO listar(UsuarioAutenticado admin, String grupo, String pagina) {
        exigirAdmin(admin);
        var errores = new ArrayList<ErrorCampoDTO>();
        var abiertos = GRUPO_ABIERTOS.equals(grupo);
        if (!abiertos && !GRUPO_RESUELTOS.equals(grupo)) {
            errores.add(new ErrorCampoDTO(CAMPO_GRUPO, MENSAJE_GRUPO));
        }
        var numero = VistaReclamos.numeroDePagina(pagina);
        if (numero == 0) {
            errores.add(new ErrorCampoDTO(BusquedaService.CAMPO_PAGINA, BusquedaService.MENSAJE_PAGINA_INVALIDA));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        var solicitud = PageRequest.of(numero - 1, TAMANO_PAGINA, abiertos ? MAS_ANTIGUOS_PRIMERO
                : RESUELTOS_RECIENTES);
        if (solicitud.getOffset() > Integer.MAX_VALUE) {
            return new PaginaReclamosDTO(numero, List.of(), false);
        }
        var trozo = abiertos ? reclamos.findByEstado(EstadoReclamo.ABIERTO, solicitud)
                : reclamos.findByEstadoIn(RESUELTOS, solicitud);
        return new PaginaReclamosDTO(numero, vista.resumenes(trozo.getContent()), trozo.hasNext());
    }

    @Transactional(readOnly = true)
    public DetalleReclamoAdminDTO detalle(UsuarioAutenticado admin, Long reclamoId) {
        exigirAdmin(admin);
        var reclamo = reclamos.findById(reclamoId).orElseThrow(RecursoNoEncontradoException::new);
        return vista.paraAdmin(reclamo);
    }

    @Transactional
    public DetalleReclamoAdminDTO resolver(UsuarioAutenticado admin, Long reclamoId, ResolucionReclamoDTO datos,
                                           String ip) {
        exigirAdmin(admin);
        var resolucion = SanitizadorTexto.limpiar(datos.resolucion());
        if (resolucion.isEmpty()) {
            throw new ReglaNegocioException(CAMPO_RESOLUCION, ResolucionReclamoDTO.M_RESOLUCION);
        }
        var aFavorDe = PapelReclamo.valueOf(datos.aFavorDe());
        // Bajo el bloqueo: de dos resoluciones simultáneas, la segunda ve el reclamo ya resuelto (R55).
        var reclamo = reclamos.findParaActualizar(reclamoId).orElseThrow(RecursoNoEncontradoException::new);
        if (!reclamo.estaAbierto()) {
            throw new ReglaNegocioException(MENSAJE_YA_RESUELTO);
        }
        var ahora = Instant.now(reloj);
        var detalle = "reservaId=" + reclamo.getReservaId() + ";aFavorDe=" + aFavorDe.name();
        BigDecimal monto = null;
        if (aFavorDe == PapelReclamo.USUARIO) {
            var tipo = tipoDeReembolso(datos.reembolso());
            var pagado = vista.montoPagado(reclamo.getReservaId()).orElseThrow(() -> new IllegalStateException(
                    "La reserva " + reclamo.getReservaId() + " no tiene un pago PAGADO que reembolsar"));
            monto = tipo == TipoReembolsoReclamo.TOTAL ? pagado : montoParcial(datos.montoReembolso(), pagado);
            reclamo.resolverAFavorDelUsuario(admin.usuarioId(), resolucion, monto, ahora);
            reclamos.saveAndFlush(reclamo);
            pagos.reembolsarReclamo(new ReembolsoPorReclamo(reclamo.getReservaId(), monto, admin.usuarioId(), ip));
            detalle += ";reembolso=" + tipo.name() + ";monto=" + monto.toPlainString();
        } else {
            // R51: reembolso y montoReembolso se ignoran.
            reclamo.resolverAFavorDelArrendador(admin.usuarioId(), resolucion, ahora);
            reclamos.saveAndFlush(reclamo);
        }
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), ACCION_RESUELTO, ReclamoService.ENTIDAD_RECLAMO,
                reclamo.getId(), ip, detalle));
        var reserva = vista.reservaDe(reclamo).orElse(null);
        if (reserva != null) {
            var correoUsuario = cuentas.correoDe(reclamo.getUsuarioId());
            var correoArrendador = cuentas.correoDe(reclamo.getArrendadorId());
            avisos.reclamoResuelto(correoUsuario, correoArrendador, new DatosResolucion(reserva.espacioNombre(),
                    reserva.fecha(), reserva.horaInicio(), reserva.horaFin(), aFavorDe, resolucion, monto));
        }
        return vista.paraAdmin(reclamo, reserva);
    }

    private static TipoReembolsoReclamo tipoDeReembolso(String texto) {
        if (TipoReembolsoReclamo.TOTAL.name().equals(texto)) {
            return TipoReembolsoReclamo.TOTAL;
        }
        if (TipoReembolsoReclamo.PARCIAL.name().equals(texto)) {
            return TipoReembolsoReclamo.PARCIAL;
        }
        throw new ReglaNegocioException(CAMPO_REEMBOLSO, MENSAJE_REEMBOLSO);
    }

    // DT-12: 0 < monto < pagado, con hasta 2 decimales. El total lo calcula siempre el servidor.
    private static BigDecimal montoParcial(BigDecimal monto, BigDecimal pagado) {
        if (monto == null || monto.stripTrailingZeros().scale() > 2 || monto.signum() <= 0
                || monto.compareTo(pagado) >= 0) {
            throw new ReglaNegocioException(CAMPO_MONTO, MENSAJE_MONTO);
        }
        return monto.setScale(2, RoundingMode.UNNECESSARY);
    }

    // Copiado como en los otros servicios del admin (DT-19): extraerlo es de HU-27.
    private static void exigirAdmin(UsuarioAutenticado admin) {
        if (admin == null || !ROL_ADMIN.equals(admin.rol())) {
            throw new AccesoDenegadoException();
        }
    }
}
