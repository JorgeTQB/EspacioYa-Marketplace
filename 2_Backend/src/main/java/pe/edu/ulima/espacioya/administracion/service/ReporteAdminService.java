package pe.edu.ulima.espacioya.administracion.service;

import static pe.edu.ulima.espacioya.comun.seguridad.RolesSesion.exigirAdmin;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.domain.DecisionReporte;
import pe.edu.ulima.espacioya.administracion.domain.EstadoReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;
import pe.edu.ulima.espacioya.administracion.dto.DecisionReporteDTO;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReporteAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.PaginaReportesDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosEspacioOcultado;
import pe.edu.ulima.espacioya.busqueda.service.BusquedaService;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.texto.SanitizadorTexto;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.EspacioOcultado;
import pe.edu.ulima.espacioya.espacios.service.ModeracionEspacios;
import pe.edu.ulima.espacioya.reservas.service.CancelacionPorModeracion;

/**
 * Cola de moderación y decisión del admin (HU-27 CA2, CA3, RF-F-11, RF-F-12). El rol se comprueba antes de leer.
 * Decidir bloquea primero los reportes hermanos (en orden de id) y después el contenido (DT-7, DT-23); ocultar,
 * cancelar por fraude, resolver y auditar van en una sola transacción y los correos salen tras el commit.
 */
@Service
public class ReporteAdminService {

    public static final String ACCION_RESUELTO = "REPORTE_RESUELTO";
    public static final String MENSAJE_YA_RESUELTO = "Este reporte ya se resolvió.";
    public static final String MENSAJE_FRAUDE_SOLO_ESPACIOS =
            "Solo puedes ocultar por fraude un espacio. Para una reseña, elige ocultar.";
    public static final String MENSAJE_GRUPO = "Elige reportes por revisar o resueltos.";
    public static final int TAMANO_PAGINA = 10;
    public static final String GRUPO_PENDIENTES = "pendientes";
    public static final String GRUPO_RESUELTOS = "resueltos";

    private static final String CAMPO_GRUPO = "grupo";
    private static final String CAMPO_DECISION = "decision";
    private static final String CAMPO_MOTIVO_DECISION = "motivoDecision";
    private static final Sort MAS_ANTIGUOS_PRIMERO = Sort.by(Sort.Direction.ASC, "creadoEn", "id");
    private static final Sort RESUELTOS_RECIENTES = Sort.by(Sort.Direction.DESC, "resueltoEn", "id");
    private static final List<EstadoReporte> RESUELTOS = List.of(EstadoReporte.MANTENIDO, EstadoReporte.OCULTADO);

    private final ReporteRepository reportes;
    private final ModeracionEspacios espacios;
    private final ModeracionResenas resenas;
    private final CancelacionPorModeracion cancelacion;
    private final CuentaService cuentas;
    private final VistaReportes vista;
    private final AvisosModeracion avisos;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public ReporteAdminService(ReporteRepository reportes, ModeracionEspacios espacios, ModeracionResenas resenas,
                               CancelacionPorModeracion cancelacion, CuentaService cuentas, VistaReportes vista,
                               AvisosModeracion avisos, AuditoriaService auditoria, Clock reloj) {
        this.reportes = reportes;
        this.espacios = espacios;
        this.resenas = resenas;
        this.cancelacion = cancelacion;
        this.cuentas = cuentas;
        this.vista = vista;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PaginaReportesDTO listar(UsuarioAutenticado admin, String grupo, String pagina) {
        exigirAdmin(admin);
        var errores = new ArrayList<ErrorCampoDTO>();
        var pendientes = GRUPO_PENDIENTES.equals(grupo);
        if (!pendientes && !GRUPO_RESUELTOS.equals(grupo)) {
            errores.add(new ErrorCampoDTO(CAMPO_GRUPO, MENSAJE_GRUPO));
        }
        var numero = VistaReclamos.numeroDePagina(pagina);
        if (numero == 0) {
            errores.add(new ErrorCampoDTO(BusquedaService.CAMPO_PAGINA, BusquedaService.MENSAJE_PAGINA_INVALIDA));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        var solicitud = PageRequest.of(numero - 1, TAMANO_PAGINA,
                pendientes ? MAS_ANTIGUOS_PRIMERO : RESUELTOS_RECIENTES);
        // Spring Data rechaza un offset mayor que un int, pero esa página es válida: no tiene nada (HU-24 R34).
        if (solicitud.getOffset() > Integer.MAX_VALUE) {
            return new PaginaReportesDTO(numero, List.of(), false);
        }
        var trozo = pendientes ? reportes.findByEstado(EstadoReporte.POR_REVISAR, solicitud)
                : reportes.findByEstadoIn(RESUELTOS, solicitud);
        return new PaginaReportesDTO(numero, vista.resumenes(trozo.getContent()), trozo.hasNext());
    }

    @Transactional(readOnly = true)
    public DetalleReporteAdminDTO detalle(UsuarioAutenticado admin, Long reporteId) {
        exigirAdmin(admin);
        var reporte = reportes.findById(reporteId).orElseThrow(RecursoNoEncontradoException::new);
        return vista.detalle(reporte, null);
    }

    @Transactional
    public DetalleReporteAdminDTO decidir(UsuarioAutenticado admin, Long reporteId, DecisionReporteDTO datos,
                                          String ip) {
        exigirAdmin(admin);
        var motivo = SanitizadorTexto.limpiar(datos.motivoDecision());
        if (motivo.isEmpty()) {
            throw new ReglaNegocioException(CAMPO_MOTIVO_DECISION, DecisionReporteDTO.M_MOTIVO_DECISION);
        }
        var decision = DecisionReporte.valueOf(datos.decision());
        // Sin bloqueo: solo para saber qué contenido señala, que no cambia nunca.
        var leido = reportes.findById(reporteId).orElseThrow(RecursoNoEncontradoException::new);
        if (decision.esFraude() && !leido.esDeEspacio()) {
            throw new ReglaNegocioException(CAMPO_DECISION, MENSAJE_FRAUDE_SOLO_ESPACIOS);
        }
        // R42: bajo el bloqueo de los hermanos, una decisión simultánea ya no ve este reporte POR_REVISAR.
        var hermanos = leido.esDeEspacio() ? reportes.bloquearPorRevisarDeEspacio(leido.getEspacioId())
                : reportes.bloquearPorRevisarDeResena(leido.getResenaId());
        var reporte = hermanos.stream().filter(hermano -> hermano.getId().equals(reporteId)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException(MENSAJE_YA_RESUELTO));
        EspacioOcultado ocultado = null;
        if (decision.oculta()) {
            if (reporte.esDeEspacio()) {
                ocultado = espacios.ocultar(admin, reporte.getEspacioId(), ip);
            } else {
                resenas.ocultar(admin, reporte.getResenaId(), ip);
            }
        }
        var canceladas = decision.esFraude()
                ? cancelacion.cancelarPorFraude(admin, reporte.getEspacioId(), ip).cantidad() : 0;
        var ahora = Instant.now(reloj);
        hermanos.forEach(hermano -> hermano.resolver(decision, admin.usuarioId(), motivo, ahora));
        reportes.saveAllAndFlush(hermanos);
        auditar(admin, reporte, decision, hermanos.size(), canceladas, ip);
        if (ocultado != null) {
            avisos.espacioOcultado(cuentas.correoDe(ocultado.arrendadorId()),
                    new DatosEspacioOcultado(ocultado.nombre(), motivo, decision.esFraude(), canceladas));
        }
        return vista.detalle(reporte, canceladas);
    }

    // Sin el motivo de la decisión: queda en el reporte (R51).
    private void auditar(UsuarioAutenticado admin, Reporte reporte, DecisionReporte decision, int resueltos,
                         int canceladas, String ip) {
        var detalle = "tipo=" + reporte.getTipoObjetivo().name() + ";objetivoId=" + reporte.getObjetivoId()
                + ";decision=" + decision.name() + ";reportes=" + resueltos + ";reservasCanceladas=" + canceladas;
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), ACCION_RESUELTO, ReporteService.ENTIDAD_REPORTE,
                reporte.getId(), ip, detalle));
    }
}
