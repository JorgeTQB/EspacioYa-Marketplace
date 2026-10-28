package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.domain.ValidadorDocumento;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class RegistroArrendadorService {

    public static final String ACCION_REGISTRO_ARRENDADOR = "REGISTRO_ARRENDADOR";
    public static final String ACCION_DOCUMENTO_EXISTENTE = "REGISTRO_DOCUMENTO_EXISTENTE";

    private static final String ENTIDAD_USUARIO = "usuario";
    private static final String DETALLE_EXISTENTE = "registro=ARRENDADOR";

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final PasswordEncoder encoder;
    private final PoliticaPrivacidad politica;
    private final NotificadorCorreo notificador;
    private final AuditoriaService auditoria;
    private final HuellaCampos huella;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public RegistroArrendadorService(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                                     PasswordEncoder encoder, PoliticaPrivacidad politica,
                                     NotificadorCorreo notificador, AuditoriaService auditoria,
                                     HuellaCampos huella, TransactionTemplate transacciones, Clock reloj) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.encoder = encoder;
        this.politica = politica;
        this.notificador = notificador;
        this.auditoria = auditoria;
        this.huella = huella;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    public void registrar(RegistroArrendadorDTO solicitud, String ip) {
        var correo = Usuario.normalizarCorreo(solicitud.correo());
        var numero = ValidadorDocumento.normalizar(solicitud.numeroDocumento());
        var tipo = TipoDocumento.valueOf(solicitud.tipoDocumento());
        // Todas las validaciones van antes de consultar la BD: un registro inválido responde igual
        // exista o no el correo o el documento.
        if (!politica.esVigente(solicitud.versionPolitica())) {
            throw new ReglaNegocioException("versionPolitica", RegistroUsuarioDTO.MENSAJE_VERSION_DESACTUALIZADA);
        }
        if (!PoliticaContrasena.cumple(solicitud.contrasena(), correo)) {
            throw new ReglaNegocioException("contrasena", PoliticaContrasena.REQUISITOS);
        }
        ValidadorDocumento.error(tipo, numero).ifPresent(mensaje -> {
            throw new ReglaNegocioException("numeroDocumento", mensaje);
        });
        // Hash y huella se calculan siempre para que el tiempo de respuesta no delate si algo ya existe.
        var hash = encoder.encode(solicitud.contrasena());
        var huellaDocumento = huella.calcular(numero);
        var nuevo = new NuevoArrendador(correo, hash, solicitud.nombre(), solicitud.telefono(), tipo, numero,
                huellaDocumento, solicitud.razonSocial(), solicitud.direccionFiscal());
        try {
            transacciones.executeWithoutResult(estado -> registrarEnTransaccion(nuevo, ip));
        } catch (DataIntegrityViolationException carrera) {
            // Otra solicitud ganó uq_usuario_correo o uq_perfil_arrendador_hash_documento.
            transacciones.executeWithoutResult(estado -> {
                if (!auditarSiExiste(correo, huellaDocumento, ip)) {
                    throw new IllegalStateException("La cuenta o el perfil que ganó la carrera no aparece");
                }
            });
        }
    }

    private void registrarEnTransaccion(NuevoArrendador nuevo, String ip) {
        if (auditarSiExiste(nuevo.correo(), nuevo.huellaDocumento(), ip)) {
            return;
        }
        var version = politica.versionVigente();
        var usuario = usuarios.saveAndFlush(Usuario.registrarArrendador(nuevo.correo(), nuevo.hash(),
                nuevo.nombre(), nuevo.telefono(), version, Instant.now(reloj)));
        perfiles.saveAndFlush(PerfilArrendador.crear(usuario.getId(), nuevo.tipo(), nuevo.numero(),
                nuevo.huellaDocumento(), nuevo.razonSocial(), nuevo.direccionFiscal()));
        auditar(ACCION_REGISTRO_ARRENDADOR, usuario.getId(), ip, "versionPolitica=" + version);
        notificador.notificarVerificacion(nuevo.correo());
    }

    // Primero el correo y luego el documento: si ambos existen gana el correo.
    private boolean auditarSiExiste(String correo, String huellaDocumento, String ip) {
        var cuenta = usuarios.findByCorreo(correo);
        if (cuenta.isPresent()) {
            auditar(RegistroService.ACCION_CORREO_EXISTENTE, cuenta.get().getId(), ip, DETALLE_EXISTENTE);
            return true;
        }
        var perfil = perfiles.findByHashDocumento(huellaDocumento);
        if (perfil.isPresent()) {
            auditar(ACCION_DOCUMENTO_EXISTENTE, perfil.get().getUsuarioId(), ip, DETALLE_EXISTENTE);
            return true;
        }
        return false;
    }

    private void auditar(String accion, Long usuarioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, ENTIDAD_USUARIO, usuarioId, ip, detalle));
    }

    private record NuevoArrendador(String correo, String hash, String nombre, String telefono, TipoDocumento tipo,
                                   String numero, String huellaDocumento, String razonSocial,
                                   String direccionFiscal) {

        @Override
        public String toString() {
            return "NuevoArrendador[***]";
        }
    }
}
