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
import pe.edu.ulima.espacioya.cuentas.domain.MotivoRechazoInicioSesion;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.InicioSesionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionIniciadaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class InicioSesionService {

    public static final String ACCION_LOGIN_EXITOSO = "LOGIN_EXITOSO";
    public static final String ACCION_LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String ACCION_LOGIN_BLOQUEADO = "LOGIN_BLOQUEADO";
    public static final int MAXIMO_FALLOS = 5;
    public static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);

    private static final String ENTIDAD_SESION = "sesion";
    private static final String ENTIDAD_USUARIO = "usuario";
    static final String PREFIJO_CLAVE = "login|";

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final SesionRepository sesiones;
    private final PasswordEncoder encoder;
    private final LimitadorIntentos limitador;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;
    private final IpsPermitidasAdmin ipsAdmin;
    private final String hashSenuelo;

    public InicioSesionService(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                               SesionRepository sesiones, PasswordEncoder encoder, LimitadorIntentos limitador,
                               AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj,
                               IpsPermitidasAdmin ipsAdmin) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.sesiones = sesiones;
        this.encoder = encoder;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.ipsAdmin = ipsAdmin;
        this.hashSenuelo = encoder.encode(TokenAleatorio.generar());
    }

    public SesionIniciadaDTO iniciar(InicioSesionDTO solicitud, String ip) {
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
        var token = TokenAleatorio.generar();
        transacciones.executeWithoutResult(estado -> {
            var sesion = sesiones.save(Sesion.iniciar(cuenta.getId(), TokenAleatorio.hash(token), Instant.now(reloj)));
            auditar(ACCION_LOGIN_EXITOSO, cuenta.getId(), ENTIDAD_SESION, sesion.getId(), ip, null);
        });
        limitador.limpiar(clave);
        return new SesionIniciadaDTO(token, cuenta.getRol().name(), AvisoCuenta.para(cuenta, perfiles));
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
        // Hasta HU-17 (MFA), el admin solo entra desde una IP permitida (HU-06, D1). Se mira después de la
        // contraseña: una contraseña incorrecta se sigue auditando como tal.
        if (usuario.get().esAdmin() && !ipsAdmin.permite(ip)) {
            return Optional.of(MotivoRechazoInicioSesion.IP_NO_PERMITIDA);
        }
        return usuario.get().motivoRechazoInicioSesion();
    }

    private void auditar(String accion, Long usuarioId, String entidad, Long entidadId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, entidad, entidadId, ip, detalle));
    }
}
