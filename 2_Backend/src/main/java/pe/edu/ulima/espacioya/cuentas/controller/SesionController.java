package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
import pe.edu.ulima.espacioya.cuentas.dto.InicioSesionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionActualDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionIniciadaDTO;
import pe.edu.ulima.espacioya.cuentas.service.InicioSesionService;
import pe.edu.ulima.espacioya.cuentas.service.SesionService;

@RestController
@RequestMapping("/api/sesiones")
public class SesionController {

    private final InicioSesionService inicioSesionService;
    private final SesionService sesionService;

    public SesionController(InicioSesionService inicioSesionService, SesionService sesionService) {
        this.inicioSesionService = inicioSesionService;
        this.sesionService = sesionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SesionIniciadaDTO iniciar(@Valid @RequestBody InicioSesionDTO solicitud, HttpServletRequest peticion) {
        return inicioSesionService.iniciar(solicitud, IpCliente.de(peticion));
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
