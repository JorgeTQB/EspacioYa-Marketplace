package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;

/**
 * Contraseña para cambiar el correo o la facturación (HU-20 STRIDE S, RNF-SEG-03). Un solo cupo de fallos por cuenta
 * para las dos rutas (D6): alternarlas no duplica los intentos. Se llama sin transacción abierta, así la auditoría del
 * fallo se confirma sola y bcrypt no retiene una conexión (como HU-18).
 */
@Component
public class ReautenticacionPerfil {

    public static final int MAXIMO_FALLOS = 5;
    public static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);
    public static final String ACCION_CONTRASENA_FALLIDA = "PERFIL_CONTRASENA_FALLIDA";
    public static final String CAMPO_CONTRASENA = "contrasena";
    public static final String MENSAJE_CONTRASENA = "Ingresa tu contraseña para confirmar.";
    public static final String MENSAJE_CONTRASENA_INCORRECTA = "La contraseña no es correcta.";

    static final String PREFIJO_CLAVE = "perfil|";
    private static final String ENTIDAD_USUARIO = "usuario";

    public enum Operacion { CORREO, FACTURACION }

    private final CuentaService cuentas;
    private final LimitadorIntentos limitador;
    private final AuditoriaService auditoria;

    public ReautenticacionPerfil(CuentaService cuentas, LimitadorIntentos limitador, AuditoriaService auditoria) {
        this.cuentas = cuentas;
        this.limitador = limitador;
        this.auditoria = auditoria;
    }

    /**
     * 429 si la cuenta superó el cupo (sin comprobar la contraseña); contraseña incorrecta → cuenta el fallo, lo audita y
     * lanza un 400 en {@code contrasena}; correcta → reinicia el contador.
     */
    public void exigir(UsuarioAutenticado usuario, String contrasena, Operacion operacion, String ip) {
        var id = usuario.usuarioId();
        var clave = clave(usuario);
        if (limitador.superaLimite(clave, MAXIMO_FALLOS, VENTANA_FALLOS)) {
            throw new DemasiadosIntentosException();
        }
        if (contrasena == null || contrasena.isBlank()) {
            throw new ReglaNegocioException(CAMPO_CONTRASENA, MENSAJE_CONTRASENA);
        }
        if (!cuentas.confirmarContrasena(usuario, contrasena)) {
            limitador.registrar(clave);
            auditoria.registrar(new EventoAuditoria(id, ACCION_CONTRASENA_FALLIDA, ENTIDAD_USUARIO, id, ip,
                    "operacion=" + operacion.name()));
            throw new ReglaNegocioException(CAMPO_CONTRASENA, MENSAJE_CONTRASENA_INCORRECTA);
        }
        limitador.limpiar(clave);
    }

    /** Cuenta un fallo sin contraseña incorrecta: el documento de otro arrendador (R42, D13). */
    public void registrarFallo(UsuarioAutenticado usuario) {
        limitador.registrar(clave(usuario));
    }

    private static String clave(UsuarioAutenticado usuario) {
        return PREFIJO_CLAVE + usuario.usuarioId();
    }
}
