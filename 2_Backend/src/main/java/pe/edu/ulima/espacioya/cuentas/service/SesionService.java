package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.seguridad.ValidadorSesion;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.dto.SesionActualDTO;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class SesionService implements ValidadorSesion {

    public static final String ACCION_CIERRE_SESION = "CIERRE_SESION";

    private static final String ENTIDAD_SESION = "sesion";

    private final SesionRepository sesiones;
    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final MfaRepository mfas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public SesionService(SesionRepository sesiones, UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                         MfaRepository mfas, AuditoriaService auditoria, Clock reloj) {
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.mfas = mfas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // Una cuenta no ACTIVA no usa sus sesiones aunque sigan vigentes: cierra la carrera entre un inicio de sesión y una
    // suspensión, en la que nacería una sesión que nadie revoca (HU-09 D3, R21). Tampoco la de un admin sin MFA
    // activo: sesiones anteriores a HU-17 o de un admin al que se le borró el MFA (HU-17 DT-3, R28). No se renueva.
    @Override
    @Transactional
    public Optional<UsuarioAutenticado> validar(String token) {
        var ahora = Instant.now(reloj);
        return sesiones.findByHashToken(TokenAleatorio.hash(token))
                .filter(sesion -> sesion.estaVigente(ahora))
                .flatMap(sesion -> usuarios.findById(sesion.getUsuarioId())
                        .filter(usuario -> usuario.getEstado() == EstadoUsuario.ACTIVA)
                        .filter(usuario -> !usuario.esAdmin() || mfas.existsByUsuarioIdAndActivoTrue(usuario.getId()))
                        .map(usuario -> {
                            sesion.renovar(ahora);
                            return new UsuarioAutenticado(usuario.getId(), sesion.getId(), usuario.getRol().name());
                        }));
    }

    @Transactional(readOnly = true)
    public SesionActualDTO actual(UsuarioAutenticado usuario) {
        var cuenta = usuarios.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
        return new SesionActualDTO(cuenta.getRol().name(), AvisoCuenta.para(cuenta, perfiles));
    }

    @Transactional
    public void cerrar(UsuarioAutenticado usuario, String ip) {
        var sesion = sesiones.findById(usuario.sesionId())
                .orElseThrow(() -> new IllegalStateException("La sesión autenticada no existe"));
        sesion.revocar();
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_CIERRE_SESION, ENTIDAD_SESION,
                sesion.getId(), ip, null));
    }
}
