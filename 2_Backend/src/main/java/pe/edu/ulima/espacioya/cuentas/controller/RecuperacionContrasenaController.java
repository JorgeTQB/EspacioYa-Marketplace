package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.MensajeDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RestablecimientoContrasenaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SolicitudRecuperacionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.TokenRecuperacionDTO;
import pe.edu.ulima.espacioya.cuentas.service.RecuperacionContrasenaService;

// El token viaja siempre en el cuerpo, nunca en la URL: así no queda en los logs de acceso (HU-04, DT-1).
@RestController
@RequestMapping("/api/recuperaciones")
public class RecuperacionContrasenaController {

    private final RecuperacionContrasenaService recuperacionService;

    public RecuperacionContrasenaController(RecuperacionContrasenaService recuperacionService) {
        this.recuperacionService = recuperacionService;
    }

    // 202 y el mismo mensaje exista o no la cuenta: la respuesta no debe revelarlo.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensajeDTO solicitar(@Valid @RequestBody SolicitudRecuperacionDTO solicitud,
                                HttpServletRequest peticion) {
        recuperacionService.solicitar(solicitud.correo(), IpCliente.de(peticion));
        return new MensajeDTO(RecuperacionContrasenaService.MENSAJE_SOLICITUD_RECIBIDA);
    }

    @PostMapping("/validaciones")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validar(@RequestBody TokenRecuperacionDTO solicitud) {
        recuperacionService.validar(solicitud.token());
    }

    @PostMapping("/confirmaciones")
    public MensajeDTO restablecer(@Valid @RequestBody RestablecimientoContrasenaDTO solicitud,
                                  HttpServletRequest peticion) {
        recuperacionService.restablecer(solicitud, IpCliente.de(peticion));
        return new MensajeDTO(RecuperacionContrasenaService.MENSAJE_CONTRASENA_RESTABLECIDA);
    }
}
