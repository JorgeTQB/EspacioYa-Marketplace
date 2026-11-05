package pe.edu.ulima.espacioya.espacios.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.service.EstadoEspacioService;

// Pausa, reanudación y baja del propio espacio (HU-21 DT-1): sin cuerpo; el arrendador sale de la sesión.
@RestController
@RequestMapping("/api/arrendadores/actual/espacios/{id}")
public class EstadoEspacioController {

    private final EstadoEspacioService estados;

    public EstadoEspacioController(EstadoEspacioService estados) {
        this.estados = estados;
    }

    @PostMapping("/pausa")
    public EspacioDTO pausar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                             HttpServletRequest peticion) {
        return estados.pausar(usuario, id, IpCliente.de(peticion));
    }

    @PostMapping("/reanudacion")
    public EspacioDTO reanudar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                               HttpServletRequest peticion) {
        return estados.reanudar(usuario, id, IpCliente.de(peticion));
    }

    @PostMapping("/baja")
    public EspacioDTO darDeBaja(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                                HttpServletRequest peticion) {
        return estados.darDeBaja(usuario, id, IpCliente.de(peticion));
    }
}
