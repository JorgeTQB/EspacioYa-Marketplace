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
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorRecibidoDTO;
import pe.edu.ulima.espacioya.cuentas.service.RegistroArrendadorService;

@RestController
@RequestMapping("/api/arrendadores")
public class ArrendadorController {

    public static final String AVISO_IDENTIDAD = PerfilArrendador.AVISO_IDENTIDAD;

    private final RegistroArrendadorService registroArrendadorService;

    public ArrendadorController(RegistroArrendadorService registroArrendadorService) {
        this.registroArrendadorService = registroArrendadorService;
    }

    // 202 también si el correo o el documento ya existen: la respuesta no debe revelar si hay una cuenta.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistroArrendadorRecibidoDTO registrar(@Valid @RequestBody RegistroArrendadorDTO solicitud,
                                                   HttpServletRequest peticion) {
        registroArrendadorService.registrar(solicitud, IpCliente.de(peticion));
        return new RegistroArrendadorRecibidoDTO(UsuarioController.MENSAJE_REGISTRO_RECIBIDO, AVISO_IDENTIDAD);
    }
}
