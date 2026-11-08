package pe.edu.ulima.espacioya.busqueda.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.PaginaResenasDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaCreadaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.ResenasPendientesDTO;
import pe.edu.ulima.espacioya.busqueda.service.ResenaService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;

@RestController
public class ResenaController {

    private final ResenaService resenas;

    public ResenaController(ResenaService resenas) {
        this.resenas = resenas;
    }

    @PostMapping("/api/resenas")
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaCreadaDTO crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                 @Valid @RequestBody CrearResenaDTO datos, HttpServletRequest peticion) {
        return resenas.crear(usuario, datos, IpCliente.de(peticion));
    }

    @GetMapping("/api/resenas/pendientes")
    public ResenasPendientesDTO pendientes(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return resenas.pendientes(usuario);
    }

    // Los dos llegan como texto: un id malo es el mismo 404 de la ficha y va antes de validar la página (R33).
    @GetMapping(ReglasLimiteFicha.RUTA_RESENAS)
    public PaginaResenasDTO deEspacio(@PathVariable String id, @RequestParam(required = false) String pagina) {
        return resenas.deEspacio(id, pagina);
    }
}
