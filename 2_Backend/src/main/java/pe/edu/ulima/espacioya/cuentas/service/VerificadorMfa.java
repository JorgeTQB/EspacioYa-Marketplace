package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.ContextoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.FactorMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;

/**
 * Comprueba códigos TOTP y de respaldo y lleva el límite de fallos por cuenta (HU-17 DT-8): 10 en 15 minutos,
 * compartido por el inicio de sesión, la activación y la desactivación. El detalle de auditoría nunca lleva el código.
 */
@Component
public class VerificadorMfa {

    public static final String ACCION_CODIGO_FALLIDO = "MFA_CODIGO_FALLIDO";
    public static final String ACCION_BLOQUEADO = "MFA_BLOQUEADO";
    public static final String ACCION_CODIGO_RESPALDO_USADO = "MFA_CODIGO_RESPALDO_USADO";
    public static final int MAXIMO_FALLOS_USUARIO = 10;
    public static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);
    public static final String MENSAJE_CODIGO_INVALIDO = "El código no es válido o ya se usó.";

    static final String PREFIJO_CLAVE = "mfa|";
    private static final String ENTIDAD_USUARIO = "usuario";

    private final LimitadorIntentos limitador;
    private final CodigoRespaldoRepository codigos;
    private final GeneradorMfa generador;
    private final AuditoriaService auditoria;

    public VerificadorMfa(LimitadorIntentos limitador, CodigoRespaldoRepository codigos, GeneradorMfa generador,
                          AuditoriaService auditoria) {
        this.limitador = limitador;
        this.codigos = codigos;
        this.generador = generador;
        this.auditoria = auditoria;
    }

    public boolean bloqueado(Long usuarioId, String ip) {
        if (!limitador.superaLimite(PREFIJO_CLAVE + usuarioId, MAXIMO_FALLOS_USUARIO, VENTANA_FALLOS)) {
            return false;
        }
        auditar(ACCION_BLOQUEADO, usuarioId, ip, null);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FactorMfa> comprobar(Mfa mfa, String codigo, boolean admiteRespaldo, ContextoMfa contexto,
                                         String ip, Instant ahora) {
        var paso = Totp.pasoAceptado(mfa.secretoEnBytes(), codigo, ahora, mfa.getUltimoPaso());
        if (paso.isPresent()) {
            mfa.registrarPaso(paso.getAsLong());
            return Optional.of(FactorMfa.TOTP);
        }
        if (!admiteRespaldo || !mfa.estaActivo()) {
            return Optional.empty();
        }
        var normalizado = GeneradorMfa.normalizar(codigo);
        if (normalizado.length() != GeneradorMfa.LARGO_CODIGO || !Base32.esBase32(normalizado)) {
            return Optional.empty();
        }
        var usuarioId = mfa.getUsuarioId();
        var respaldo = codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(usuarioId, generador.huella(normalizado));
        if (respaldo.isEmpty()) {
            return Optional.empty();
        }
        respaldo.get().usar(ahora);
        // Se escribe el uso antes de contar: el conteo de restantes ya no debe incluirlo.
        codigos.flush();
        var restantes = codigos.countByUsuarioIdAndUsadoEnIsNull(usuarioId);
        auditar(ACCION_CODIGO_RESPALDO_USADO, usuarioId, ip, "contexto=" + contexto + ";restantes=" + restantes);
        return Optional.of(FactorMfa.RESPALDO);
    }

    public void registrarFallo(Long usuarioId, ContextoMfa contexto, String ip) {
        limitador.registrar(PREFIJO_CLAVE + usuarioId);
        auditar(ACCION_CODIGO_FALLIDO, usuarioId, ip, "contexto=" + contexto);
    }

    public void limpiarFallos(Long usuarioId) {
        limitador.limpiar(PREFIJO_CLAVE + usuarioId);
    }

    private void auditar(String accion, Long usuarioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, ENTIDAD_USUARIO, usuarioId, ip, detalle));
    }
}
