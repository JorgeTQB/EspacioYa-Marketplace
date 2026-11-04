package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.MotivoRechazoCambioCorreo;
import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Token;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.SolicitudCambioCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil.Operacion;

/**
 * Cambio de correo en dos pasos (HU-20 CA2): la solicitud, con sesión y contraseña, deja el correo pendiente y envía
 * el enlace al nuevo y un aviso al actual; la confirmación, pública, lo activa con el token del enlace (D21). Ningún
 * método es transaccional: los rechazos se auditan aunque no haya cambios y bcrypt no retiene una conexión.
 */
@Service
public class CambioCorreoService {

    public static final String ACCION_SOLICITADO = "CORREO_CAMBIO_SOLICITADO";
    public static final String ACCION_NO_EMITIDO = "CORREO_CAMBIO_NO_EMITIDO";
    public static final String ACCION_CAMBIADO = "CORREO_CAMBIADO";
    public static final String ACCION_RECHAZADO = "CORREO_CAMBIO_RECHAZADO";
    public static final int MAXIMO_SOLICITUDES = 3;
    public static final Duration VENTANA_SOLICITUDES = Duration.ofHours(1);

    public static final String MENSAJE_MISMO_CORREO = "Ese ya es tu correo.";
    public static final String MENSAJE_SOLICITUD_RECIBIDA = "Si el correo está disponible, te enviamos un enlace para "
            + "confirmarlo. Tu correo actual seguirá activo hasta que lo confirmes.";
    public static final String MENSAJE_ENLACE_INVALIDO =
            "El enlace para confirmar tu correo no es válido o ya expiró. Solicita el cambio de nuevo.";
    public static final String MENSAJE_CORREO_CONFIRMADO =
            "Tu correo se cambió. Desde ahora inicia sesión con tu nuevo correo.";

    static final String PREFIJO_CLAVE = "cambio-correo|";
    static final String RUTA_ENLACE = "/confirmar-correo#token=";
    private static final String CAMPO_CORREO = "correo";
    private static final String ENTIDAD_USUARIO = "usuario";
    private static final String ENTIDAD_TOKEN = "token";

    private final UsuarioRepository usuarios;
    private final TokenRepository tokens;
    private final ReautenticacionPerfil reautenticacion;
    private final NotificadorCorreo notificador;
    private final LimitadorIntentos limitador;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;
    private final String urlFrontend;

    public CambioCorreoService(UsuarioRepository usuarios, TokenRepository tokens,
                               ReautenticacionPerfil reautenticacion, NotificadorCorreo notificador,
                               LimitadorIntentos limitador, AuditoriaService auditoria,
                               TransactionTemplate transacciones, Clock reloj,
                               @Value("${espacioya.frontend.url}") String urlFrontend) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.reautenticacion = reautenticacion;
        this.notificador = notificador;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.urlFrontend = urlFrontend.endsWith("/") ? urlFrontend.substring(0, urlFrontend.length() - 1)
                : urlFrontend;
    }

    /**
     * Responde igual (sin error) si el correo nuevo ya tiene cuenta: guarda el pendiente y avisa al actual, pero no
     * emite token ni enlace (R29, RNF-SEG-13).
     */
    public void solicitar(UsuarioAutenticado usuario, SolicitudCambioCorreoDTO solicitud, String ip) {
        PerfilService.exigirTitular(usuario);
        reautenticacion.exigir(usuario, solicitud.contrasena(), Operacion.CORREO, ip);
        var id = usuario.usuarioId();
        var nuevo = Usuario.normalizarCorreo(solicitud.correo());
        var actual = usuarios.findById(id)
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
        if (nuevo.equals(actual.getCorreo())) {
            throw new ReglaNegocioException(CAMPO_CORREO, MENSAJE_MISMO_CORREO);
        }
        // Después de la contraseña: un tercero con la sesión no gasta el cupo del titular (D18).
        if (!limitador.intentar(PREFIJO_CLAVE + id, MAXIMO_SOLICITUDES, VENTANA_SOLICITUDES)) {
            throw new DemasiadosIntentosException();
        }
        var enUso = usuarios.findByCorreo(nuevo).isPresent();
        var token = enUso ? null : TokenAleatorio.generar();
        var enmascarado = Usuario.enmascararCorreo(nuevo);
        transacciones.executeWithoutResult(estado -> {
            var ahora = Instant.now(reloj);
            var cuenta = usuarios.findParaActualizar(id)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
            if (nuevo.equals(cuenta.getCorreo())) {
                throw new ReglaNegocioException(CAMPO_CORREO, MENSAJE_MISMO_CORREO);
            }
            var correoActual = cuenta.getCorreo();
            cuenta.solicitarCambioCorreo(nuevo);
            tokens.anularPendientes(id, TipoToken.CAMBIO_CORREO, ahora);
            if (token == null) {
                auditar(ACCION_NO_EMITIDO, id, ENTIDAD_USUARIO, id, ip,
                        "motivo=" + MotivoRechazoCambioCorreo.CORREO_EN_USO.name() + ";nuevo=" + enmascarado);
            } else {
                var guardado = tokens.save(Token.cambioCorreo(id, TokenAleatorio.hash(token), ahora));
                auditar(ACCION_SOLICITADO, id, ENTIDAD_TOKEN, guardado.getId(), ip, "nuevo=" + enmascarado);
                DespuesDelCommit.ejecutar(
                        () -> notificador.notificarCambioCorreo(nuevo, urlFrontend + RUTA_ENLACE + token));
            }
            DespuesDelCommit.ejecutar(() -> notificador.notificarAvisoCambioCorreo(correoActual, enmascarado));
        });
    }

    public void confirmar(String token, String ip) {
        var comprobacion = comprobar(token);
        if (comprobacion.motivo() != null) {
            rechazar(comprobacion, comprobacion.motivo(), ip);
        }
        MotivoRechazoCambioCorreo motivo;
        try {
            motivo = transacciones.execute(estado -> {
                var ahora = Instant.now(reloj);
                // Mismo orden de bloqueos que solicitar (usuario → token): una solicitud nueva y la confirmación de la
                // anterior de la misma cuenta se ordenan sin deadlock, y la segunda de dos confirmaciones espera aquí.
                usuarios.findParaActualizar(comprobacion.usuarioId())
                        .orElseThrow(() -> new IllegalStateException("La cuenta de un token vigente no existe"));
                // UPDATE condicional: de dos confirmaciones simultáneas solo una lo marca (R33).
                if (tokens.marcarUsado(comprobacion.tokenId(), ahora) == 0) {
                    return MotivoRechazoCambioCorreo.TOKEN_USADO;
                }
                // marcarUsado limpia el contexto de persistencia: se relee la cuenta (la fila ya está bloqueada).
                var cuenta = usuarios.findParaActualizar(comprobacion.usuarioId())
                        .orElseThrow(() -> new IllegalStateException("La cuenta de un token vigente no existe"));
                // El pendiente se revisa solo aquí, bajo el bloqueo y tras marcar el token: fuera de la transacción una
                // confirmación simultánea ya confirmada lo dejaría nulo y se auditaría un motivo que no es el real (R50).
                if (!habilitada(cuenta)) {
                    return MotivoRechazoCambioCorreo.CUENTA_NO_HABILITADA;
                }
                var pendiente = cuenta.getCorreoPendiente();
                if (usuarios.findByCorreo(pendiente).isPresent()) {
                    // Sin consumir el token: no se cambia nada y el titular puede pedir otro correo.
                    estado.setRollbackOnly();
                    return MotivoRechazoCambioCorreo.CORREO_EN_USO;
                }
                var anterior = cuenta.getCorreo();
                cuenta.confirmarCambioCorreo();
                usuarios.saveAndFlush(cuenta);
                // D4: las sesiones van por id de usuario, no por correo; no se revocan.
                auditar(ACCION_CAMBIADO, cuenta.getId(), ENTIDAD_USUARIO, cuenta.getId(), ip,
                        "anterior=" + Usuario.enmascararCorreo(anterior) + ";nuevo="
                                + Usuario.enmascararCorreo(pendiente));
                return null;
            });
        } catch (DataIntegrityViolationException e) {
            // Otra cuenta tomó el correo entre la búsqueda y el UPDATE (uq_usuario_correo).
            motivo = MotivoRechazoCambioCorreo.CORREO_EN_USO;
        }
        if (motivo != null) {
            rechazar(comprobacion, motivo, ip);
        }
    }

    private Comprobacion comprobar(String token) {
        // La forma se revisa antes de calcular el hash o consultar la tabla.
        if (!TokenAleatorio.tieneFormato(token)) {
            return new Comprobacion(MotivoRechazoCambioCorreo.TOKEN_DESCONOCIDO, null, null);
        }
        var encontrado = tokens.findByHashTokenAndTipo(TokenAleatorio.hash(token), TipoToken.CAMBIO_CORREO);
        if (encontrado.isEmpty()) {
            return new Comprobacion(MotivoRechazoCambioCorreo.TOKEN_DESCONOCIDO, null, null);
        }
        var cambio = encontrado.get();
        var usuarioId = cambio.getUsuarioId();
        if (cambio.fueUsado()) {
            return new Comprobacion(MotivoRechazoCambioCorreo.TOKEN_USADO, usuarioId, cambio.getId());
        }
        if (cambio.expiro(Instant.now(reloj))) {
            return new Comprobacion(MotivoRechazoCambioCorreo.TOKEN_EXPIRADO, usuarioId, cambio.getId());
        }
        if (usuarios.findById(usuarioId).filter(CambioCorreoService::activa).isEmpty()) {
            return new Comprobacion(MotivoRechazoCambioCorreo.CUENTA_NO_HABILITADA, usuarioId, cambio.getId());
        }
        return new Comprobacion(null, usuarioId, cambio.getId());
    }

    private static boolean activa(Usuario cuenta) {
        return cuenta.getEstado() == EstadoUsuario.ACTIVA;
    }

    private static boolean habilitada(Usuario cuenta) {
        return activa(cuenta) && cuenta.getCorreoPendiente() != null;
    }

    private void rechazar(Comprobacion comprobacion, MotivoRechazoCambioCorreo motivo, String ip) {
        auditar(ACCION_RECHAZADO, comprobacion.usuarioId(), ENTIDAD_TOKEN, comprobacion.tokenId(), ip,
                "motivo=" + motivo.name());
        throw new ReglaNegocioException(MENSAJE_ENLACE_INVALIDO);
    }

    private void auditar(String accion, Long usuarioId, String entidad, Long entidadId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle));
    }

    private record Comprobacion(MotivoRechazoCambioCorreo motivo, Long usuarioId, Long tokenId) {
    }
}
