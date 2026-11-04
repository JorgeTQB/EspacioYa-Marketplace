package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.CodigoMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CodigosRespaldoDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ConfiguracionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DesactivacionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.EstadoMfaDTO;
import pe.edu.ulima.espacioya.cuentas.service.MfaService;

@RestController
@RequestMapping("/api/mfa")
public class MfaController {

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    @GetMapping
    public ResponseEntity<EstadoMfaDTO> estado(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mfaService.estado(usuario));
    }

    @PostMapping("/configuracion")
    public ResponseEntity<ConfiguracionMfaDTO> configurar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mfaService.iniciarConfiguracion(usuario));
    }

    @PostMapping("/activacion")
    public ResponseEntity<CodigosRespaldoDTO> activar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                      @Valid @RequestBody CodigoMfaDTO solicitud,
                                                      HttpServletRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mfaService.activar(usuario, solicitud, IpCliente.de(peticion)));
    }

    @PostMapping("/desactivacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                           @Valid @RequestBody DesactivacionMfaDTO solicitud, HttpServletRequest peticion) {
        mfaService.desactivar(usuario, solicitud, IpCliente.de(peticion));
    }
}
