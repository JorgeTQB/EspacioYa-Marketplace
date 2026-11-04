package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.ContextoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.CodigoMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CodigosRespaldoDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ConfiguracionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DesactivacionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.EstadoMfaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/** MFA desde la cuenta con sesión: estado, activación y desactivación (HU-17 CA2, CA3, P5). */
@Service
public class MfaService {

    public static final String ACCION_MFA_DESACTIVADO = "MFA_DESACTIVADO";
    public static final String MENSAJE_YA_ACTIVO = "La verificación en dos pasos ya está activa en tu cuenta.";
    public static final String MENSAJE_SIN_CONFIGURACION = "Primero genera tu clave de configuración.";
    public static final String MENSAJE_NO_ACTIVO = "La verificación en dos pasos no está activa en tu cuenta.";
    public static final String MENSAJE_DESACTIVACION_RECHAZADA = "La contraseña o el código no son correctos.";

    private static final String ENTIDAD_USUARIO = "usuario";

    private final UsuarioRepository usuarios;
    private final MfaRepository mfas;
    private final CodigoRespaldoRepository codigos;
    private final SesionRepository sesiones;
    private final VerificadorMfa verificador;
    private final GeneradorMfa generador;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public MfaService(UsuarioRepository usuarios, MfaRepository mfas, CodigoRespaldoRepository codigos,
                      SesionRepository sesiones, VerificadorMfa verificador, GeneradorMfa generador,
                      PasswordEncoder encoder, AuditoriaService auditoria, TransactionTemplate transacciones,
                      Clock reloj) {
        this.usuarios = usuarios;
        this.mfas = mfas;
        this.codigos = codigos;
        this.sesiones = sesiones;
        this.verificador = verificador;
        this.generador = generador;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public EstadoMfaDTO estado(UsuarioAutenticado usuario) {
        var activo = mfas.findById(usuario.usuarioId()).map(Mfa::estaActivo).orElse(false);
        var restantes = activo ? codigos.countByUsuarioIdAndUsadoEnIsNull(usuario.usuarioId()) : 0;
        return new EstadoMfaDTO(activo, restantes, activo && !esAdmin(usuario));
    }

    @Transactional
    public ConfiguracionMfaDTO iniciarConfiguracion(UsuarioAutenticado usuario) {
        var ahora = Instant.now(reloj);
        var cuenta = cuentaBloqueada(usuario);
        var mfa = mfas.findParaActualizar(cuenta.getId());
        if (mfa.isPresent() && mfa.get().estaActivo()) {
            throw new ReglaNegocioException(MENSAJE_YA_ACTIVO);
        }
        var secreto = generador.nuevoSecreto();
        mfa.ifPresentOrElse(
                pendiente -> pendiente.reemplazarSecreto(secreto, ahora),
                () -> mfas.save(Mfa.pendiente(cuenta.getId(), secreto, ahora)));
        return generador.configuracion(cuenta.getCorreo(), secreto);
    }

    // Como en VerificacionMfaService: el fallo se guarda en la transacción y la excepción se lanza fuera (DT-11).
    public CodigosRespaldoDTO activar(UsuarioAutenticado usuario, CodigoMfaDTO solicitud, String ip) {
        var resultado = transacciones.execute(estado -> procesarActivacion(usuario, solicitud, ip));
        return switch (resultado.tipo()) {
            case ACEPTADO -> {
                verificador.limpiarFallos(usuario.usuarioId());
                yield new CodigosRespaldoDTO(resultado.codigos());
            }
            case BLOQUEADO -> throw new DemasiadosIntentosException();
            case SIN_CONFIGURACION -> throw new ReglaNegocioException(MENSAJE_SIN_CONFIGURACION);
            case RECHAZADO -> throw new ReglaNegocioException("codigo", VerificadorMfa.MENSAJE_CODIGO_INVALIDO);
        };
    }

    public void desactivar(UsuarioAutenticado usuario, DesactivacionMfaDTO solicitud, String ip) {
        if (esAdmin(usuario)) {
            throw new AccesoDenegadoException();
        }
        var resultado = transacciones.execute(estado -> procesarDesactivacion(usuario, solicitud, ip));
        switch (resultado.tipo()) {
            case ACEPTADO -> verificador.limpiarFallos(usuario.usuarioId());
            case BLOQUEADO -> throw new DemasiadosIntentosException();
            case SIN_CONFIGURACION -> throw new ReglaNegocioException(MENSAJE_NO_ACTIVO);
            case RECHAZADO -> throw new ReglaNegocioException(MENSAJE_DESACTIVACION_RECHAZADA);
        }
    }

    private Resultado procesarActivacion(UsuarioAutenticado usuario, CodigoMfaDTO solicitud, String ip) {
        var ahora = Instant.now(reloj);
        var cuenta = cuentaBloqueada(usuario);
        var usuarioId = cuenta.getId();
        var mfa = mfas.findParaActualizar(usuarioId);
        if (verificador.bloqueado(usuarioId, ip)) {
            return Resultado.de(Tipo.BLOQUEADO);
        }
        if (mfa.isEmpty() || mfa.get().estaActivo()) {
            return Resultado.de(Tipo.SIN_CONFIGURACION);
        }
        var factor = verificador.comprobar(mfa.get(), solicitud.codigo(), false, ContextoMfa.ACTIVACION, ip, ahora);
        if (factor.isEmpty()) {
            verificador.registrarFallo(usuarioId, ContextoMfa.ACTIVACION, ip);
            return Resultado.de(Tipo.RECHAZADO);
        }
        mfa.get().activar(ahora);
        var nuevos = generador.reemplazarCodigos(usuarioId, ahora);
        auditar(VerificacionMfaService.ACCION_MFA_ACTIVADO, usuarioId, ip, "contexto=CUENTA");
        // Al final: revocarOtras desprende las entidades cargadas (DT-12). La sesión de la solicitud sigue.
        sesiones.revocarOtras(usuarioId, usuario.sesionId());
        return new Resultado(Tipo.ACEPTADO, nuevos);
    }

    // Contraseña y código se comprueban siempre los dos: ni la respuesta ni el tiempo dicen cuál falló (R41).
    private Resultado procesarDesactivacion(UsuarioAutenticado usuario, DesactivacionMfaDTO solicitud, String ip) {
        var ahora = Instant.now(reloj);
        var cuenta = cuentaBloqueada(usuario);
        var usuarioId = cuenta.getId();
        var mfa = mfas.findParaActualizar(usuarioId);
        if (verificador.bloqueado(usuarioId, ip)) {
            return Resultado.de(Tipo.BLOQUEADO);
        }
        if (mfa.isEmpty() || !mfa.get().estaActivo()) {
            return Resultado.de(Tipo.SIN_CONFIGURACION);
        }
        var contrasenaCorrecta = encoder.matches(solicitud.contrasena(), cuenta.getHashContrasena());
        var factor = verificador.comprobar(mfa.get(), solicitud.codigo(), true, ContextoMfa.DESACTIVACION, ip,
                ahora);
        if (!contrasenaCorrecta || factor.isEmpty()) {
            verificador.registrarFallo(usuarioId, ContextoMfa.DESACTIVACION, ip);
            return Resultado.de(Tipo.RECHAZADO);
        }
        codigos.borrarDeUsuario(usuarioId);
        mfas.delete(mfa.get());
        auditar(ACCION_MFA_DESACTIVADO, usuarioId, ip, null);
        return Resultado.de(Tipo.ACEPTADO);
    }

    private Usuario cuentaBloqueada(UsuarioAutenticado usuario) {
        return usuarios.findParaActualizar(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
    }

    private static boolean esAdmin(UsuarioAutenticado usuario) {
        return Rol.ADMIN.name().equals(usuario.rol());
    }

    private void auditar(String accion, Long usuarioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, ENTIDAD_USUARIO, usuarioId, ip, detalle));
    }

    private enum Tipo { ACEPTADO, BLOQUEADO, SIN_CONFIGURACION, RECHAZADO }

    private record Resultado(Tipo tipo, List<String> codigos) {

        static Resultado de(Tipo tipo) {
            return new Resultado(tipo, null);
        }
    }
}
