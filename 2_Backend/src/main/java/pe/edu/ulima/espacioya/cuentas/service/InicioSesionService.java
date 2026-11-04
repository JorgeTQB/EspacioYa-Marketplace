package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.CredencialesInvalidasException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.IpsPermitidasAdmin;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.DesafioMfa;
import pe.edu.ulima.espacioya.cuentas.domain.MotivoRechazoInicioSesion;
import pe.edu.ulima.espacioya.cuentas.domain.PasoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.DesafioMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.InicioSesionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RespuestaInicioSesion;
import pe.edu.ulima.espacioya.cuentas.repository.DesafioMfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class InicioSesionService {

    public static final String ACCION_LOGIN_EXITOSO = "LOGIN_EXITOSO";
    public static final String ACCION_LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String ACCION_LOGIN_BLOQUEADO = "LOGIN_BLOQUEADO";
    public static final String ACCION_LOGIN_MFA_PENDIENTE = "LOGIN_MFA_PENDIENTE";
    public static final int MAXIMO_FALLOS = 5;
    public static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);

    private static final String ENTIDAD_USUARIO = "usuario";
    private static final String ENTIDAD_DESAFIO = "desafio_mfa";
    static final String PREFIJO_CLAVE = "login|";

    private final UsuarioRepository usuarios;
    private final MfaRepository mfas;
    private final DesafioMfaRepository desafios;
    private final EmisorSesiones emisor;
    private final PasswordEncoder encoder;
    private final LimitadorIntentos limitador;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;
    private final IpsPermitidasAdmin ipsAdmin;
    private final String hashSenuelo;

    public InicioSesionService(UsuarioRepository usuarios, MfaRepository mfas, DesafioMfaRepository desafios,
                               EmisorSesiones emisor, PasswordEncoder encoder, LimitadorIntentos limitador,
                               AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj,
                               IpsPermitidasAdmin ipsAdmin) {
        this.usuarios = usuarios;
        this.mfas = mfas;
        this.desafios = desafios;
        this.emisor = emisor;
        this.encoder = encoder;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.ipsAdmin = ipsAdmin;
        this.hashSenuelo = encoder.encode(TokenAleatorio.generar());
    }

    public RespuestaInicioSesion iniciar(InicioSesionDTO solicitud, String ip) {
        var correo = Usuario.normalizarCorreo(solicitud.correo());
        var clave = PREFIJO_CLAVE + correo;
        if (limitador.superaLimite(clave, MAXIMO_FALLOS, VENTANA_FALLOS)) {
            var usuarioId = usuarios.findByCorreo(correo).map(Usuario::getId).orElse(null);
            auditar(ACCION_LOGIN_BLOQUEADO, usuarioId, ENTIDAD_USUARIO, usuarioId, ip, null);
            throw new DemasiadosIntentosException();
        }

        var usuario = usuarios.findByCorreo(correo);
        var motivo = motivoRechazo(usuario, solicitud.contrasena(), ip);
        if (motivo.isPresent()) {
            var usuarioId = usuario.map(Usuario::getId).orElse(null);
            limitador.registrar(clave);
            auditar(ACCION_LOGIN_FALLIDO, usuarioId, ENTIDAD_USUARIO, usuarioId, ip, "motivo=" + motivo.get());
            throw new CredencialesInvalidasException();
        }

        var cuenta = usuario.orElseThrow();
        var respuesta = transacciones.execute(estado -> responder(cuenta, ip));
        limitador.limpiar(clave);
        return respuesta;
    }

    // Con MFA activo se pide el código; el admin sin MFA debe configurarlo antes de tener sesión (HU-17 CA1, DT-2).
    // Las credenciales ya se aceptaron: el desafío no es una sesión, solo se canjea en POST /api/sesiones/mfa.
    private RespuestaInicioSesion responder(Usuario cuenta, String ip) {
        PasoMfa paso;
        if (mfas.existsByUsuarioIdAndActivoTrue(cuenta.getId())) {
            paso = PasoMfa.CODIGO;
        } else if (cuenta.esAdmin()) {
            paso = PasoMfa.CONFIGURACION;
        } else {
            return emisor.emitir(cuenta, ip, null);
        }
        var token = TokenAleatorio.generar();
        var desafio = desafios.save(DesafioMfa.crear(cuenta.getId(), TokenAleatorio.hash(token), paso,
                Instant.now(reloj)));
        auditar(ACCION_LOGIN_MFA_PENDIENTE, cuenta.getId(), ENTIDAD_DESAFIO, desafio.getId(), ip, "paso=" + paso);
        return new DesafioMfaDTO(token, paso);
    }

    private Optional<MotivoRechazoInicioSesion> motivoRechazo(Optional<Usuario> usuario, String contrasena,
                                                              String ip) {
        if (usuario.isEmpty()) {
            // Comparación contra un hash señuelo para que el tiempo de respuesta no revele si el correo existe.
            encoder.matches(contrasena, hashSenuelo);
            return Optional.of(MotivoRechazoInicioSesion.CORREO_DESCONOCIDO);
        }
        if (!encoder.matches(contrasena, usuario.get().getHashContrasena())) {
            return Optional.of(MotivoRechazoInicioSesion.CONTRASENA_INCORRECTA);
        }
        // El admin solo entra desde una IP permitida (HU-06, D1), que complementa a MFA (HU-17, P3). Se mira después
        // de la contraseña: una contraseña incorrecta se sigue auditando como tal.
        if (usuario.get().esAdmin() && !ipsAdmin.permite(ip)) {
            return Optional.of(MotivoRechazoInicioSesion.IP_NO_PERMITIDA);
        }
        return usuario.get().motivoRechazoInicioSesion();
    }

    private void auditar(String accion, Long usuarioId, String entidad, Long entidadId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle));
    }
}
