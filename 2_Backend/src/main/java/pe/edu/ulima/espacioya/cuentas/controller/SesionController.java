package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.ConfiguracionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DesafioDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DesafioMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.InicioSesionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionActualDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionIniciadaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionVerificadaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.VerificacionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.service.InicioSesionService;
import pe.edu.ulima.espacioya.cuentas.service.SesionService;
import pe.edu.ulima.espacioya.cuentas.service.VerificacionMfaService;

@RestController
@RequestMapping("/api/sesiones")
public class SesionController {

    private final InicioSesionService inicioSesionService;
    private final SesionService sesionService;
    private final VerificacionMfaService verificacionMfaService;

    public SesionController(InicioSesionService inicioSesionService, SesionService sesionService,
                            VerificacionMfaService verificacionMfaService) {
        this.inicioSesionService = inicioSesionService;
        this.sesionService = sesionService;
        this.verificacionMfaService = verificacionMfaService;
    }

    // ResponseEntity<?> y no <RespuestaInicioSesion>: Jackson serializa el record real, no la interfaz vacía.
    @PostMapping
    public ResponseEntity<?> iniciar(@Valid @RequestBody InicioSesionDTO solicitud, HttpServletRequest peticion) {
        return switch (inicioSesionService.iniciar(solicitud, IpCliente.de(peticion))) {
            case SesionIniciadaDTO sesion -> ResponseEntity.status(HttpStatus.CREATED).body(sesion);
            case DesafioMfaDTO desafio -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .cacheControl(CacheControl.noStore())
                    .body(desafio);
        };
    }

    @PostMapping("/mfa")
    public ResponseEntity<SesionVerificadaDTO> verificarMfa(@Valid @RequestBody VerificacionMfaDTO solicitud,
                                                            HttpServletRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(verificacionMfaService.verificar(solicitud, IpCliente.de(peticion)));
    }

    @PostMapping("/mfa/configuracion")
    public ResponseEntity<ConfiguracionMfaDTO> configurarMfa(@Valid @RequestBody DesafioDTO solicitud,
                                                             HttpServletRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(verificacionMfaService.configurar(solicitud, IpCliente.de(peticion)));
    }

    @GetMapping("/actual")
    public SesionActualDTO actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return sesionService.actual(usuario);
    }

    @DeleteMapping("/actual")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrar(@AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest peticion) {
        sesionService.cerrar(usuario, IpCliente.de(peticion));
    }
}
