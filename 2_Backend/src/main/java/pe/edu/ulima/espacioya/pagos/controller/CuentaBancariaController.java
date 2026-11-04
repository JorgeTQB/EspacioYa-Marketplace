package pe.edu.ulima.espacioya.pagos.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.pagos.dto.CuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.dto.GuardarCuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.dto.MiCuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.service.CuentaBancariaService;

/**
 * Cuenta bancaria del arrendador de la sesión (HU-18). Ninguna ruta lleva el id del arrendador (R4); el rol lo
 * comprueba el servicio. Toda respuesta lleva {@code no-store} (R21).
 */
@RestController
@RequestMapping("/api/arrendadores/actual/cuenta-bancaria")
public class CuentaBancariaController {

    private final CuentaBancariaService servicio;

    public CuentaBancariaController(CuentaBancariaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<MiCuentaBancariaDTO> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(usuario));
    }

    // 200 en el registro y en el cambio: la respuesta es el estado de la cuenta, no un recurso nuevo (DT-12).
    @PutMapping
    public ResponseEntity<CuentaBancariaDTO> guardar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                     @Valid @RequestBody GuardarCuentaBancariaDTO solicitud,
                                                     HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.guardar(usuario, solicitud, IpCliente.de(peticion)));
    }
}
