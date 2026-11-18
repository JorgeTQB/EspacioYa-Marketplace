package pe.edu.ulima.espacioya.cuentas.controller;

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
import pe.edu.ulima.espacioya.cuentas.dto.PreferenciasCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.service.PreferenciasCorreoService;

/**
 * Preferencia de recordatorios del titular de la sesión (HU-25 D5). Sin id en la ruta ni en el cuerpo: la cuenta sale
 * de la sesión (R3); el rol lo comprueba el servicio (R5). Toda respuesta lleva {@code no-store} (R7).
 */
@RestController
@RequestMapping("/api/usuarios/actual/preferencias-correo")
public class PreferenciasCorreoController {

    private final PreferenciasCorreoService preferencias;

    public PreferenciasCorreoController(PreferenciasCorreoService preferencias) {
        this.preferencias = preferencias;
    }

    @GetMapping
    public ResponseEntity<PreferenciasCorreoDTO> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return sinCache(preferencias.consultar(usuario));
    }

    @PutMapping
    public ResponseEntity<PreferenciasCorreoDTO> actualizar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                            @Valid @RequestBody PreferenciasCorreoDTO datos,
                                                            HttpServletRequest peticion) {
        return sinCache(preferencias.actualizar(usuario, datos, IpCliente.de(peticion)));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
