package pe.edu.ulima.espacioya.espacios.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.dto.EspacioResumenDTO;
import pe.edu.ulima.espacioya.espacios.dto.FichaEspacioDTO;
import pe.edu.ulima.espacioya.espacios.service.EspacioService;

// La identidad sale de la sesión (/actual), nunca de la URL ni del cuerpo (HU-07 DT-7).
@RestController
@RequestMapping("/api/arrendadores/actual/espacios")
public class EspacioController {

    private final EspacioService espacioService;

    public EspacioController(EspacioService espacioService) {
        this.espacioService = espacioService;
    }

    @GetMapping
    public List<EspacioResumenDTO> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return espacioService.listar(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EspacioDTO crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
                            @Valid @RequestBody FichaEspacioDTO ficha, HttpServletRequest peticion) {
        return espacioService.crear(usuario, ficha, IpCliente.de(peticion));
    }

    @GetMapping("/{id}")
    public EspacioDTO consultar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return espacioService.consultar(usuario, id);
    }

    @PutMapping("/{id}")
    public EspacioDTO editar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                             @Valid @RequestBody FichaEspacioDTO ficha, HttpServletRequest peticion) {
        return espacioService.editar(usuario, id, ficha, IpCliente.de(peticion));
    }

    @PostMapping("/{id}/publicacion")
    public EspacioDTO publicar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                               HttpServletRequest peticion) {
        return espacioService.publicar(usuario, id, IpCliente.de(peticion));
    }
}
