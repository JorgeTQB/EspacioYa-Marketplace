package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class RegistroService {

    public static final String ACCION_REGISTRO = "REGISTRO_USUARIO";
    public static final String ACCION_CORREO_EXISTENTE = "REGISTRO_CORREO_EXISTENTE";

    private static final String ENTIDAD_USUARIO = "usuario";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final PoliticaPrivacidad politica;
    private final NotificadorCorreo notificador;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public RegistroService(UsuarioRepository usuarios, PasswordEncoder encoder,
                           PoliticaPrivacidad politica, NotificadorCorreo notificador,
                           AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.politica = politica;
        this.notificador = notificador;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    public void registrar(RegistroUsuarioDTO solicitud, String ip) {
        var correo = Usuario.normalizarCorreo(solicitud.correo());
        if (!politica.esVigente(solicitud.versionPolitica())) {
            throw new ReglaNegocioException("versionPolitica", RegistroUsuarioDTO.MENSAJE_VERSION_DESACTUALIZADA);
        }
        if (!PoliticaContrasena.cumple(solicitud.contrasena(), correo)) {
            throw new ReglaNegocioException("contrasena", PoliticaContrasena.REQUISITOS);
        }
        // El hash se calcula siempre, exista o no el correo, para que el tiempo de respuesta no lo delate.
        var hash = encoder.encode(solicitud.contrasena());
        try {
            transacciones.executeWithoutResult(estado -> registrarEnTransaccion(correo, hash, ip));
        } catch (DataIntegrityViolationException carrera) {
            // Otra solicitud con el mismo correo ganó la restricción uq_usuario_correo.
            transacciones.executeWithoutResult(estado -> auditarCorreoExistente(correo, ip));
        }
    }

    private void registrarEnTransaccion(String correo, String hash, String ip) {
        var existente = usuarios.findByCorreo(correo);
        if (existente.isPresent()) {
            auditar(ACCION_CORREO_EXISTENTE, existente.get().getId(), ip, null);
            return;
        }
        var version = politica.versionVigente();
        var usuario = usuarios.saveAndFlush(Usuario.registrar(correo, hash, version, Instant.now(reloj)));
        auditar(ACCION_REGISTRO, usuario.getId(), ip, "versionPolitica=" + version);
        notificador.notificarVerificacion(correo);
    }

    private void auditarCorreoExistente(String correo, String ip) {
        var titular = usuarios.findByCorreo(correo)
                .orElseThrow(() -> new IllegalStateException("La cuenta que ganó la carrera no aparece"));
        auditar(ACCION_CORREO_EXISTENTE, titular.getId(), ip, null);
    }

    private void auditar(String accion, Long usuarioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, ENTIDAD_USUARIO, usuarioId, ip, detalle));
    }
}
