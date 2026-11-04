package pe.edu.ulima.espacioya.pagos.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.MotivoNoReintentable;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.domain.ReembolsoPorAtender;
import pe.edu.ulima.espacioya.pagos.domain.ReglasPago;
import pe.edu.ulima.espacioya.pagos.dto.ReembolsoAdminDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.reembolso.Disparo;
import pe.edu.ulima.espacioya.pagos.service.reembolso.EjecutorReembolsos;
import pe.edu.ulima.espacioya.pagos.service.reembolso.ResultadoReembolso;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudesReembolso;

/**
 * Panel admin de reembolsos (HU-16 CA2) y Client del Command al reintentar. {@code FiltroIpsAdmin} ya exigió una IP
 * permitida; aquí se exige el rol {@code ADMIN} antes de leer nada (R26). Quien actúa y su IP salen de la sesión y de la
 * conexión (R35).
 */
@Service
public class ReembolsoAdminService {

    public static final String MENSAJE_YA_REEMBOLSADO = "Este reembolso ya se realizó.";
    public static final String MENSAJE_EN_PROCESO =
            "Este reembolso se está procesando. Espera un momento y vuelve a cargar la lista.";

    private static final String ROL_ADMIN = "ADMIN";

    private final ReembolsoRepository reembolsos;
    private final PagoRepository pagos;
    private final SolicitudesReembolso solicitudes;
    private final EjecutorReembolsos ejecutor;
    private final Clock reloj;

    public ReembolsoAdminService(ReembolsoRepository reembolsos, PagoRepository pagos,
                                 SolicitudesReembolso solicitudes, EjecutorReembolsos ejecutor, Clock reloj) {
        this.reembolsos = reembolsos;
        this.pagos = pagos;
        this.solicitudes = solicitudes;
        this.ejecutor = ejecutor;
        this.reloj = reloj;
    }

    /** {@code FALLIDO} y {@code PENDIENTE} sin respuesta, del más antiguo al más reciente (R28, R29). */
    @Transactional(readOnly = true)
    public List<ReembolsoAdminDTO> porAtender(UsuarioAutenticado admin) {
        exigirAdmin(admin);
        var limite = Instant.now(reloj).minus(Reembolso.MARGEN_SIN_RESPUESTA);
        return reembolsos.porAtender(limite).stream().map(ReembolsoAdminService::dto).toList();
    }

    // Sin @Transactional: el comando abre sus propias transacciones y no debe haber filas retenidas (DT-5).
    public ReembolsoAdminDTO reintentar(UsuarioAutenticado admin, String id, String ip) {
        exigirAdmin(admin);
        var reembolsoId = ReglasPago.idBienEscrito(id);
        if (reembolsoId == null) {
            throw new RecursoNoEncontradoException();
        }
        rechazarSiNoReintentable(reembolsos.findById(reembolsoId).orElseThrow(RecursoNoEncontradoException::new));
        var resultado = ejecutor.ejecutarAhora(solicitudes.crear(reembolsoId, Disparo.ADMIN, admin.usuarioId(), ip));
        var actual = reembolsos.findById(reembolsoId).orElseThrow(RecursoNoEncontradoException::new);
        if (resultado == ResultadoReembolso.NO_EJECUTADO) {
            // Otro reintento ganó la reclamación entre la lectura y el bloqueo (R23).
            rechazarSiNoReintentable(actual);
            throw new ReglaNegocioException(MENSAJE_EN_PROCESO);
        }
        var reservaId = pagos.findById(actual.getPagoId()).orElseThrow().getReservaId();
        return new ReembolsoAdminDTO(actual.getId(), reservaId, actual.getMonto(), actual.getOrigen().name(),
                actual.getEstado().name(), actual.getIntentos(), actual.getCreadoEn(), actual.getUltimoIntentoEn());
    }

    private void rechazarSiNoReintentable(Reembolso reembolso) {
        var motivo = reembolso.motivoNoReintentable(Instant.now(reloj));
        if (motivo.isPresent()) {
            throw new ReglaNegocioException(motivo.get() == MotivoNoReintentable.YA_REEMBOLSADO
                    ? MENSAJE_YA_REEMBOLSADO : MENSAJE_EN_PROCESO);
        }
    }

    private static void exigirAdmin(UsuarioAutenticado admin) {
        if (admin == null || !ROL_ADMIN.equals(admin.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private static ReembolsoAdminDTO dto(ReembolsoPorAtender fila) {
        return new ReembolsoAdminDTO(fila.id(), fila.reservaId(), fila.monto(), fila.origen().name(),
                fila.estado().name(), fila.intentos(), fila.creadoEn(), fila.ultimoIntentoEn());
    }
}
