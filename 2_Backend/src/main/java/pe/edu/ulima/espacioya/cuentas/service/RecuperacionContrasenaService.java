package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.MotivoRechazoRecuperacion;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Token;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.RestablecimientoContrasenaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class RecuperacionContrasenaService {

    public static final String ACCION_SOLICITADA = "RECUPERACION_SOLICITADA";
    public static final String ACCION_NO_EMITIDA = "RECUPERACION_NO_EMITIDA";
    public static final String ACCION_RECHAZADA = "RECUPERACION_RECHAZADA";
    public static final String ACCION_RESTABLECIDA = "CONTRASENA_RESTABLECIDA";
    public static final int MAXIMO_SOLICITUDES = 3;
    public static final Duration VENTANA_SOLICITUDES = Duration.ofHours(1);

    public static final String MENSAJE_SOLICITUD_RECIBIDA = "Si el correo está registrado, te enviamos un enlace "
            + "para restablecer tu contraseña. Revisa tu bandeja de entrada.";
    public static final String MENSAJE_ENLACE_INVALIDO =
            "El enlace para restablecer tu contraseña no es válido o ya expiró. Solicita uno nuevo.";
    public static final String MENSAJE_CONTRASENA_RESTABLECIDA =
            "Tu contraseña se cambió. Inicia sesión con tu nueva contraseña.";

    static final String MOTIVO_LIMITE_SUPERADO = "LIMITE_SUPERADO";
    static final String MOTIVO_CORREO_DESCONOCIDO = "CORREO_DESCONOCIDO";

    private static final String ENTIDAD_TOKEN = "token";
    private static final String ENTIDAD_USUARIO = "usuario";
    private static final String PREFIJO_CLAVE = "recuperacion|";
    private static final String RUTA_ENLACE = "/restablecer-contrasena#token=";

    private final UsuarioRepository usuarios;
    private final TokenRepository tokens;
    private final SesionRepository sesiones;
    private final NotificadorCorreo notificador;
    private final PasswordEncoder encoder;
    private final LimitadorIntentos limitador;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;
    private final String urlFrontend;

    public RecuperacionContrasenaService(UsuarioRepository usuarios, TokenRepository tokens,
                                         SesionRepository sesiones, NotificadorCorreo notificador,
                                         PasswordEncoder encoder, LimitadorIntentos limitador,
                                         AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj,
                                         @Value("${espacioya.frontend.url}") String urlFrontend) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.sesiones = sesiones;
        this.notificador = notificador;
        this.encoder = encoder;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.urlFrontend = urlFrontend.endsWith("/") ? urlFrontend.substring(0, urlFrontend.length() - 1)
                : urlFrontend;
    }

    // Siempre termina sin error: el controlador responde lo mismo exista o no la cuenta (CA1, STRIDE I).
    public void solicitar(String correo, String ip) {
        var correoNormalizado = Usuario.normalizarCorreo(correo);
        // El límite se aplica antes de buscar la cuenta para que cuente igual exista o no (D3, S9).
        if (!limitador.intentar(PREFIJO_CLAVE + correoNormalizado, MAXIMO_SOLICITUDES, VENTANA_SOLICITUDES)) {
            var usuarioId = usuarios.findByCorreo(correoNormalizado).map(Usuario::getId).orElse(null);
            auditarNoEmitida(usuarioId, ip, MOTIVO_LIMITE_SUPERADO);
            return;
        }
        var usuario = usuarios.findByCorreo(correoNormalizado);
        if (usuario.isEmpty()) {
            auditarNoEmitida(null, ip, MOTIVO_CORREO_DESCONOCIDO);
            return;
        }
        var cuenta = usuario.get();
        var motivo = cuenta.motivoRechazoInicioSesion();
        if (motivo.isPresent()) {
            auditarNoEmitida(cuenta.getId(), ip, motivo.get().name());
            return;
        }
        var token = TokenAleatorio.generar();
        transacciones.executeWithoutResult(estado -> {
            var ahora = Instant.now(reloj);
            tokens.anularPendientes(cuenta.getId(), TipoToken.RECUPERACION, ahora);
            var guardado = tokens.save(Token.recuperacion(cuenta.getId(), TokenAleatorio.hash(token), ahora));
            notificador.notificarRecuperacion(correoNormalizado, urlFrontend + RUTA_ENLACE + token);
            auditar(ACCION_SOLICITADA, cuenta.getId(), ENTIDAD_TOKEN, guardado.getId(), ip, null);
        });
    }

    // Abrir el enlace no es usarlo: no se audita (S6).
    public void validar(String token) {
        if (comprobar(token).motivo() != null) {
            throw new ReglaNegocioException(MENSAJE_ENLACE_INVALIDO);
        }
    }

    public void restablecer(RestablecimientoContrasenaDTO solicitud, String ip) {
        var comprobacion = comprobar(solicitud.token());
        if (comprobacion.motivo() != null) {
            auditarRechazo(comprobacion.motivo(), comprobacion.usuarioId(), comprobacion.tokenId(), ip);
            throw new ReglaNegocioException(MENSAJE_ENLACE_INVALIDO);
        }
        var usuarioId = comprobacion.usuarioId();
        var correo = comprobacion.correo();
        if (!PoliticaContrasena.cumple(solicitud.contrasena(), correo)) {
            throw new ReglaNegocioException("contrasena", PoliticaContrasena.REQUISITOS);
        }
        // bcrypt (~250 ms) fuera de la transacción para no retener una conexión.
        var hash = encoder.encode(solicitud.contrasena());
        var restablecida = transacciones.execute(estado -> {
            var ahora = Instant.now(reloj);
            if (tokens.marcarUsado(comprobacion.tokenId(), ahora) == 0) {
                return false;
            }
            var cuenta = usuarios.findById(usuarioId)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de un token vigente no existe"));
            cuenta.cambiarContrasena(hash);
            tokens.anularPendientes(usuarioId, TipoToken.RECUPERACION, ahora);
            var revocadas = sesiones.revocarTodas(usuarioId);
            auditar(ACCION_RESTABLECIDA, usuarioId, ENTIDAD_USUARIO, usuarioId, ip, "sesionesRevocadas=" + revocadas);
            return true;
        });
        if (!Boolean.TRUE.equals(restablecida)) {
            // Otra confirmación usó el token entre la comprobación y el UPDATE condicional (R22).
            auditarRechazo(MotivoRechazoRecuperacion.TOKEN_USADO, usuarioId, comprobacion.tokenId(), ip);
            throw new ReglaNegocioException(MENSAJE_ENLACE_INVALIDO);
        }
        // Quien olvidó la contraseña suele haber agotado sus intentos de inicio de sesión (S7).
        limitador.limpiar(InicioSesionService.PREFIJO_CLAVE + correo);
    }

    private Comprobacion comprobar(String token) {
        // La forma se revisa antes de calcular el hash o consultar la tabla (R17).
        if (!TokenAleatorio.tieneFormato(token)) {
            return Comprobacion.rechazo(MotivoRechazoRecuperacion.TOKEN_DESCONOCIDO, null, null);
        }
        var encontrado = tokens.findByHashTokenAndTipo(TokenAleatorio.hash(token), TipoToken.RECUPERACION);
        if (encontrado.isEmpty()) {
            return Comprobacion.rechazo(MotivoRechazoRecuperacion.TOKEN_DESCONOCIDO, null, null);
        }
        var recuperacion = encontrado.get();
        var usuarioId = recuperacion.getUsuarioId();
        if (recuperacion.fueUsado()) {
            return Comprobacion.rechazo(MotivoRechazoRecuperacion.TOKEN_USADO, usuarioId, recuperacion.getId());
        }
        if (recuperacion.expiro(Instant.now(reloj))) {
            return Comprobacion.rechazo(MotivoRechazoRecuperacion.TOKEN_EXPIRADO, usuarioId, recuperacion.getId());
        }
        var cuenta = usuarios.findById(usuarioId).filter(u -> u.motivoRechazoInicioSesion().isEmpty());
        if (cuenta.isEmpty()) {
            return Comprobacion.rechazo(MotivoRechazoRecuperacion.CUENTA_NO_HABILITADA, usuarioId,
                    recuperacion.getId());
        }
        return new Comprobacion(null, usuarioId, recuperacion.getId(), cuenta.get().getCorreo());
    }

    private void auditarNoEmitida(Long usuarioId, String ip, String motivo) {
        auditar(ACCION_NO_EMITIDA, usuarioId, ENTIDAD_USUARIO, usuarioId, ip, "motivo=" + motivo);
    }

    private void auditarRechazo(MotivoRechazoRecuperacion motivo, Long usuarioId, Long tokenId, String ip) {
        auditar(ACCION_RECHAZADA, usuarioId, ENTIDAD_TOKEN, tokenId, ip, "motivo=" + motivo);
    }

    private void auditar(String accion, Long usuarioId, String entidad, Long entidadId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle));
    }

    private record Comprobacion(MotivoRechazoRecuperacion motivo, Long usuarioId, Long tokenId, String correo) {

        static Comprobacion rechazo(MotivoRechazoRecuperacion motivo, Long usuarioId, Long tokenId) {
            return new Comprobacion(motivo, usuarioId, tokenId, null);
        }
    }
}
