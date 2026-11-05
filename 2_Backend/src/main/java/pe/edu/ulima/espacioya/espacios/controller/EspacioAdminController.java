package pe.edu.ulima.espacioya.espacios.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.espacios.dto.EstadoEspacioDTO;
import pe.edu.ulima.espacioya.espacios.service.EstadoEspacioService;

/**
 * Pausa y baja de cualquier espacio por el admin (HU-21 DT-1, decisión 5). Antes de llegar aquí, {@code FiltroIpsAdmin}
 * exige una IP permitida y el filtro de sesión una sesión vigente; el rol {@code ADMIN} lo comprueba el servicio.
 */
@RestController
@RequestMapping("/api/admin/espacios/{id}")
public class EspacioAdminController {

    private final EstadoEspacioService estados;

    public EspacioAdminController(EstadoEspacioService estados) {
        this.estados = estados;
    }

    @PostMapping("/pausa")
    public EstadoEspacioDTO pausar(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable Long id,
                                   HttpServletRequest peticion) {
        return estados.pausarComoAdmin(admin, id, IpCliente.de(peticion));
    }

    @PostMapping("/baja")
    public EstadoEspacioDTO darDeBaja(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable Long id,
                                      HttpServletRequest peticion) {
        return estados.darDeBajaComoAdmin(admin, id, IpCliente.de(peticion));
    }
}
