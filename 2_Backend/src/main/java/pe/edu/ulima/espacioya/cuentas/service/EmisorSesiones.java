package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.SesionIniciadaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;

/** Única forma de crear una sesión (HU-03 R2, R3, R6, R30), con o sin MFA (HU-17 DT-10). */
@Component
public class EmisorSesiones {

    private static final String ENTIDAD_SESION = "sesion";

    private final SesionRepository sesiones;
    private final PerfilArrendadorRepository perfiles;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public EmisorSesiones(SesionRepository sesiones, PerfilArrendadorRepository perfiles, AuditoriaService auditoria,
                          Clock reloj) {
        this.sesiones = sesiones;
        this.perfiles = perfiles;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SesionIniciadaDTO emitir(Usuario cuenta, String ip, String detalleAuditoria) {
        var token = TokenAleatorio.generar();
        var sesion = sesiones.save(Sesion.iniciar(cuenta.getId(), TokenAleatorio.hash(token), Instant.now(reloj)));
        auditoria.registrar(new EventoAuditoria(cuenta.getId(), InicioSesionService.ACCION_LOGIN_EXITOSO,
                ENTIDAD_SESION, sesion.getId(), ip, detalleAuditoria));
        return new SesionIniciadaDTO(token, cuenta.getRol().name(), AvisoCuenta.para(cuenta, perfiles));
    }
}
