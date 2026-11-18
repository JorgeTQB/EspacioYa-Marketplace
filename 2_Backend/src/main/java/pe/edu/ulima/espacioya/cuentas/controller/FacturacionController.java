package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.ActualizacionFacturacionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.PerfilDTO;
import pe.edu.ulima.espacioya.cuentas.service.FacturacionService;
import pe.edu.ulima.espacioya.cuentas.service.PerfilService;

/** Facturación del arrendador de la sesión (HU-20 CA3). Responde con el perfil ya actualizado y {@code no-store}. */
@RestController
@RequestMapping("/api/arrendadores/actual/facturacion")
public class FacturacionController {

    private final FacturacionService facturacion;
    private final PerfilService perfiles;

    public FacturacionController(FacturacionService facturacion, PerfilService perfiles) {
        this.facturacion = facturacion;
        this.perfiles = perfiles;
    }

    @PutMapping
    public ResponseEntity<PerfilDTO> actualizar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                @Valid @RequestBody ActualizacionFacturacionDTO solicitud,
                                                HttpServletRequest peticion) {
        facturacion.actualizar(usuario, solicitud, IpCliente.de(peticion));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(perfiles.consultar(usuario));
    }
}
