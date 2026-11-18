package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.ConfirmacionCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.dto.MensajeDTO;
import pe.edu.ulima.espacioya.cuentas.service.CambioCorreoService;

/**
 * Confirmación pública del correo nuevo (HU-20 D21): la prueba es el token de 256 bits enviado a ese correo. Viaja
 * solo en el cuerpo, nunca en la URL, para que no quede en los logs de acceso (R34).
 */
@RestController
@RequestMapping("/api/cambios-correo/confirmaciones")
public class ConfirmacionCorreoController {

    private final CambioCorreoService cambiosCorreo;

    public ConfirmacionCorreoController(CambioCorreoService cambiosCorreo) {
        this.cambiosCorreo = cambiosCorreo;
    }

    @PostMapping
    public ResponseEntity<MensajeDTO> confirmar(@Valid @RequestBody ConfirmacionCorreoDTO solicitud,
                                                HttpServletRequest peticion) {
        cambiosCorreo.confirmar(solicitud.token(), IpCliente.de(peticion));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MensajeDTO(CambioCorreoService.MENSAJE_CORREO_CONFIRMADO));
    }
}
