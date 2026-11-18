package pe.edu.ulima.espacioya.administracion.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.administracion.domain.MotivoReporte;
import pe.edu.ulima.espacioya.administracion.domain.ReglasReporte;
import pe.edu.ulima.espacioya.administracion.domain.Reporte;
import pe.edu.ulima.espacioya.administracion.domain.TipoObjetivoReporte;
import pe.edu.ulima.espacioya.administracion.dto.CrearReporteDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReporteCreadoDTO;
import pe.edu.ulima.espacioya.administracion.repository.ReporteRepository;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.ModeracionEspacios;

/**
 * Reportar un espacio o una reseña (HU-27 CA1, RF-F-10). Orden (design § 4): rol → transacción con el bloqueo de la
 * cuenta → cupo de 10 en 24 h (429) → contenido reportable (404) → propio (400) → ya reportado (400) → alta →
 * auditoría. El cupo se cuenta en la base bajo el bloqueo de {@code usuario}: dos reportes simultáneos del mismo
 * reportante se serializan (DT-6, R18).
 */
@Service
public class ReporteService {

    public static final String ACCION_CREADO = "REPORTE_CREADO";
    public static final String ENTIDAD_REPORTE = "reporte";
    public static final String MENSAJE_CONTENIDO_PROPIO = "No puedes reportar tu propio contenido.";
    public static final String MENSAJE_YA_REPORTADO = "Ya reportaste este contenido. Lo revisaremos pronto.";

    private static final String ROL_USUARIO = "USUARIO";
    private static final String ROL_ARRENDADOR = "ARRENDADOR";
    private static final String RESTRICCION_ESPACIO = "uq_reporte_espacio";
    private static final String RESTRICCION_RESENA = "uq_reporte_resena";

    private final ReporteRepository reportes;
    private final ModeracionEspacios espacios;
    private final ModeracionResenas resenas;
    private final CuentaService cuentas;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public ReporteService(ReporteRepository reportes, ModeracionEspacios espacios, ModeracionResenas resenas,
                          CuentaService cuentas, AuditoriaService auditoria, TransactionTemplate transacciones,
                          Clock reloj) {
        this.reportes = reportes;
        this.espacios = espacios;
        this.resenas = resenas;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // Sin @Transactional: con él, la violación de uq_reporte_* llegaría al confirmar y no se traduciría (DT-5).
    public ReporteCreadoDTO reportar(UsuarioAutenticado reportante, CrearReporteDTO datos, String ip) {
        exigirReportante(reportante);
        var tipo = TipoObjetivoReporte.valueOf(datos.tipoObjetivo());
        var motivo = MotivoReporte.valueOf(datos.motivo());
        try {
            return transacciones.execute(estado -> reportarEnTransaccion(reportante, tipo, datos.objetivoId(),
                    motivo, ip));
        } catch (DataIntegrityViolationException conflicto) {
            if (esContenidoYaReportado(conflicto)) {
                throw new ReglaNegocioException(MENSAJE_YA_REPORTADO);
            }
            throw conflicto;
        }
    }

    private ReporteCreadoDTO reportarEnTransaccion(UsuarioAutenticado reportante, TipoObjetivoReporte tipo,
                                                   Long objetivoId, MotivoReporte motivo, String ip) {
        var reportanteId = reportante.usuarioId();
        cuentas.bloquearCuenta(reportanteId);
        // Después del bloqueo: el reloj y la cuenta ven todo lo que confirmó un reporte simultáneo (R18).
        var ahora = Instant.now(reloj);
        if (reportes.countByReportanteIdAndCreadoEnAfter(reportanteId, ReglasReporte.inicioVentana(ahora))
                >= ReglasReporte.MAXIMO_POR_DIA) {
            throw new DemasiadosIntentosException();
        }
        var reporte = tipo == TipoObjetivoReporte.ESPACIO
                ? deEspacio(reportanteId, objetivoId, motivo, ahora)
                : deResena(reportanteId, objetivoId, motivo, ahora);
        reporte = reportes.saveAndFlush(reporte);
        auditoria.registrar(new EventoAuditoria(reportanteId, ACCION_CREADO, ENTIDAD_REPORTE, reporte.getId(), ip,
                "tipo=" + tipo.name() + ";objetivoId=" + objetivoId + ";motivo=" + motivo.name()));
        return new ReporteCreadoDTO(reporte.getId(), tipo.name(), objetivoId, motivo.name(),
                reporte.getEstado().name(), reporte.getCreadoEn());
    }

    private Reporte deEspacio(Long reportanteId, Long espacioId, MotivoReporte motivo, Instant ahora) {
        var espacio = espacios.reportable(espacioId).orElseThrow(RecursoNoEncontradoException::new);
        if (reportanteId.equals(espacio.arrendadorId())) {
            throw new ReglaNegocioException(MENSAJE_CONTENIDO_PROPIO);
        }
        if (reportes.existsByReportanteIdAndEspacioId(reportanteId, espacioId)) {
            throw new ReglaNegocioException(MENSAJE_YA_REPORTADO);
        }
        return Reporte.deEspacio(reportanteId, espacioId, motivo, ahora);
    }

    private Reporte deResena(Long reportanteId, Long resenaId, MotivoReporte motivo, Instant ahora) {
        var resena = resenas.reportable(resenaId).orElseThrow(RecursoNoEncontradoException::new);
        if (reportanteId.equals(resena.autorId())) {
            throw new ReglaNegocioException(MENSAJE_CONTENIDO_PROPIO);
        }
        if (reportes.existsByReportanteIdAndResenaId(reportanteId, resenaId)) {
            throw new ReglaNegocioException(MENSAJE_YA_REPORTADO);
        }
        return Reporte.deResena(reportanteId, resenaId, motivo, ahora);
    }

    // R5: el admin modera, no reporta. Antes de leer nada ni contar el cupo.
    private static void exigirReportante(UsuarioAutenticado reportante) {
        if (reportante == null
                || !(ROL_USUARIO.equals(reportante.rol()) || ROL_ARRENDADOR.equals(reportante.rol()))) {
            throw new AccesoDenegadoException();
        }
    }

    // Por el nombre del índice: otro fallo de integridad no debe disfrazarse de reporte repetido (R12).
    private static boolean esContenidoYaReportado(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && (mensaje.contains(RESTRICCION_ESPACIO) || mensaje.contains(RESTRICCION_RESENA));
    }
}
