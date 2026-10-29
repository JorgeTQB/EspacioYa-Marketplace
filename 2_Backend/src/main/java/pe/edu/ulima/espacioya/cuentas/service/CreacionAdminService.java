package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * Crea cuentas de administrador por script interno (HU-06, D4; RNF-SEG-18). Solo lo llama {@link CreadorAdmin}:
 * no hay endpoint. Los mensajes de error nombran la variable de entorno, nunca su valor.
 */
@Service
public class CreacionAdminService {

    public static final String ACCION_CREACION_ADMIN = "CREACION_ADMIN";
    public static final String VARIABLE_CORREO = "ADMIN_CORREO";
    public static final String VARIABLE_CONTRASENA = "ADMIN_CONTRASENA";

    private static final String ENTIDAD_USUARIO = "usuario";
    private static final int LONGITUD_MAXIMA_CORREO = 254;
    private static final Pattern FORMA_CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final PoliticaPrivacidad politica;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public CreacionAdminService(UsuarioRepository usuarios, PasswordEncoder encoder, PoliticaPrivacidad politica,
                                AuditoriaService auditoria, TransactionTemplate transacciones, Clock reloj) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.politica = politica;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    public Long crear(String correo, String contrasena) {
        var correoNormalizado = Usuario.normalizarCorreo(correo);
        if (correoNormalizado == null || correoNormalizado.length() > LONGITUD_MAXIMA_CORREO
                || !FORMA_CORREO.matcher(correoNormalizado).matches()) {
            throw new IllegalStateException(VARIABLE_CORREO + " está vacía o no tiene forma de correo");
        }
        if (!PoliticaContrasena.cumple(contrasena, correoNormalizado)) {
            throw new IllegalStateException(VARIABLE_CONTRASENA + " no cumple la política de contraseña: "
                    + PoliticaContrasena.REQUISITOS);
        }
        var hash = encoder.encode(contrasena);
        try {
            return transacciones.execute(estado -> registrar(correoNormalizado, hash));
        } catch (DataIntegrityViolationException carrera) {
            throw yaExiste();
        }
    }

    private Long registrar(String correo, String hash) {
        // Una cuenta existente nunca se modifica (S15): ni se promueve a admin ni se le cambia la contraseña.
        if (usuarios.findByCorreo(correo).isPresent()) {
            throw yaExiste();
        }
        var admin = usuarios.saveAndFlush(Usuario.registrarAdmin(correo, hash, politica.versionVigente(),
                Instant.now(reloj)));
        auditoria.registrar(new EventoAuditoria(admin.getId(), ACCION_CREACION_ADMIN, ENTIDAD_USUARIO, admin.getId(),
                null, null));
        return admin.getId();
    }

    private static IllegalStateException yaExiste() {
        return new IllegalStateException("Ya existe una cuenta con el correo de " + VARIABLE_CORREO
                + ": no se creó ni se modificó ninguna cuenta");
    }
}
