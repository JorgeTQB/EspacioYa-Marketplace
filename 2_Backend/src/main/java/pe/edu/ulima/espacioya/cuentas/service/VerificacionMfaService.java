package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.VerificacionExpiradaException;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.ContextoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.DesafioMfa;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.FactorMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.PasoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.ConfiguracionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DesafioDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionVerificadaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.VerificacionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DesafioMfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/** Segundo paso del inicio de sesión: canjea un desafío MFA por la sesión (HU-17 DT-1, DT-2). */
@Service
public class VerificacionMfaService {

    public static final String ACCION_MFA_ACTIVADO = "MFA_ACTIVADO";

    private static final String ENTIDAD_USUARIO = "usuario";
    private static final Set<PasoMfa> PASOS_VERIFICACION = Set.of(PasoMfa.CODIGO, PasoMfa.CONFIGURACION);
    private static final Set<PasoMfa> PASOS_CONFIGURACION = Set.of(PasoMfa.CONFIGURACION);

    private final DesafioMfaRepository desafios;
    private final UsuarioRepository usuarios;
    private final MfaRepository mfas;
    private final SesionRepository sesiones;
    private final VerificadorMfa verificador;
    private final GeneradorMfa generador;
    private final EmisorSesiones emisor;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public VerificacionMfaService(DesafioMfaRepository desafios, UsuarioRepository usuarios, MfaRepository mfas,
                                  SesionRepository sesiones, VerificadorMfa verificador, GeneradorMfa generador,
                                  EmisorSesiones emisor, AuditoriaService auditoria,
                                  TransactionTemplate transacciones, Clock reloj) {
        this.desafios = desafios;
        this.usuarios = usuarios;
        this.mfas = mfas;
        this.sesiones = sesiones;
        this.verificador = verificador;
        this.generador = generador;
        this.emisor = emisor;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    @Transactional
    public ConfiguracionMfaDTO configurar(DesafioDTO solicitud, String ip) {
        var ahora = Instant.now(reloj);
        var contexto = bloquear(solicitud.desafio());
        if (contexto.isEmpty() || !contexto.get().vigente(PASOS_CONFIGURACION, ahora)) {
            throw new VerificacionExpiradaException();
        }
        var cuenta = contexto.get().cuenta();
        var secreto = generador.nuevoSecreto();
        contexto.get().mfa().ifPresentOrElse(
                pendiente -> pendiente.reemplazarSecreto(secreto, ahora),
                () -> mfas.save(Mfa.pendiente(cuenta.getId(), secreto, ahora)));
        return generador.configuracion(cuenta.getCorreo(), secreto);
    }

    // El resultado sale de la transacción y la excepción se lanza fuera: un fallo debe quedar guardado
    // (intentos_fallidos, auditoría) aunque la respuesta sea un error (DT-11).
    public SesionVerificadaDTO verificar(VerificacionMfaDTO solicitud, String ip) {
        var resultado = transacciones.execute(estado -> procesar(solicitud, ip));
        return switch (resultado.tipo()) {
            case ACEPTADO -> {
                verificador.limpiarFallos(resultado.usuarioId());
                yield resultado.respuesta();
            }
            case CODIGO_INVALIDO -> throw new ReglaNegocioException("codigo", VerificadorMfa.MENSAJE_CODIGO_INVALIDO);
            case BLOQUEADO -> throw new DemasiadosIntentosException();
            case EXPIRADO -> throw new VerificacionExpiradaException();
        };
    }

    private Resultado procesar(VerificacionMfaDTO solicitud, String ip) {
        var ahora = Instant.now(reloj);
        var bloqueo = bloquear(solicitud.desafio());
        if (bloqueo.isEmpty() || !bloqueo.get().vigente(PASOS_VERIFICACION, ahora)) {
            return Resultado.de(Tipo.EXPIRADO);
        }
        var desafio = bloqueo.get().desafio();
        var cuenta = bloqueo.get().cuenta();
        var usuarioId = cuenta.getId();
        if (verificador.bloqueado(usuarioId, ip)) {
            return Resultado.de(Tipo.BLOQUEADO);
        }

        var configuracion = desafio.getPaso() == PasoMfa.CONFIGURACION;
        var mfa = bloqueo.get().mfa();
        Optional<FactorMfa> factor = mfa.isEmpty() ? Optional.empty()
                : verificador.comprobar(mfa.get(), solicitud.codigo(), !configuracion, ContextoMfa.INICIO_SESION,
                        ip, ahora);
        if (factor.isEmpty()) {
            var agotado = desafio.registrarFallo(ahora);
            verificador.registrarFallo(usuarioId, ContextoMfa.INICIO_SESION, ip);
            return Resultado.de(agotado ? Tipo.EXPIRADO : Tipo.CODIGO_INVALIDO);
        }

        desafio.cerrar(ahora);
        List<String> codigos = null;
        if (configuracion) {
            mfa.get().activar(ahora);
            codigos = generador.reemplazarCodigos(usuarioId, ahora);
            auditoria.registrar(new EventoAuditoria(usuarioId, ACCION_MFA_ACTIVADO, ENTIDAD_USUARIO, usuarioId, ip,
                    "contexto=" + ContextoMfa.INICIO_SESION));
            // Después de cerrar el desafío, activar y guardar los códigos: revocarTodas desprende las entidades.
            sesiones.revocarTodas(usuarioId);
        }
        var sesion = emisor.emitir(cuenta, ip, "factor=" + factor.get());
        var respuesta = new SesionVerificadaDTO(sesion.token(), sesion.rol(), sesion.aviso(), codigos);
        return new Resultado(Tipo.ACEPTADO, usuarioId, respuesta);
    }

    // Orden fijo de bloqueos desafio_mfa → usuario → mfa (DT-11).
    private Optional<Bloqueo> bloquear(String desafio) {
        return desafios.findParaActualizar(TokenAleatorio.hash(desafio))
                .flatMap(encontrado -> usuarios.findParaActualizar(encontrado.getUsuarioId())
                        .map(cuenta -> new Bloqueo(encontrado, cuenta, mfas.findParaActualizar(cuenta.getId()))));
    }

    private record Bloqueo(DesafioMfa desafio, Usuario cuenta, Optional<Mfa> mfa) {

        // "Desafío vigente para un endpoint" (requirements, Definiciones).
        boolean vigente(Set<PasoMfa> admitidos, Instant ahora) {
            if (!desafio.estaAbierto(ahora) || !admitidos.contains(desafio.getPaso())) {
                return false;
            }
            if (!cuenta.isCorreoVerificado() || cuenta.getEstado() != EstadoUsuario.ACTIVA) {
                return false;
            }
            var activo = mfa.map(Mfa::estaActivo).orElse(false);
            return desafio.getPaso() == PasoMfa.CODIGO ? activo : !activo;
        }
    }

    private enum Tipo { ACEPTADO, CODIGO_INVALIDO, BLOQUEADO, EXPIRADO }

    private record Resultado(Tipo tipo, Long usuarioId, SesionVerificadaDTO respuesta) {

        static Resultado de(Tipo tipo) {
            return new Resultado(tipo, null, null);
        }
    }
}
