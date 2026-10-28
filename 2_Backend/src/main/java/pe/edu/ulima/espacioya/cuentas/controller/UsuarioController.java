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
import pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO;
import pe.edu.ulima.espacioya.cuentas.service.RegistroService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    public static final String MENSAJE_REGISTRO_RECIBIDO =
            "Te enviamos un correo de verificación. Revisa tu bandeja de entrada.";

    private final RegistroService registroService;

    public UsuarioController(RegistroService registroService) {
        this.registroService = registroService;
    }

    // 202 también si el correo ya existe: la respuesta no debe revelar si hay una cuenta.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensajeDTO registrar(@Valid @RequestBody RegistroUsuarioDTO solicitud, HttpServletRequest peticion) {
        registroService.registrar(solicitud, IpCliente.de(peticion));
        return new MensajeDTO(MENSAJE_REGISTRO_RECIBIDO);
    }
}
