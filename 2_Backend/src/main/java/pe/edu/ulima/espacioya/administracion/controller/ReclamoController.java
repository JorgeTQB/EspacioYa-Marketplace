package pe.edu.ulima.espacioya.administracion.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.administracion.dto.CrearReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.MensajeReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.NuevoMensajeDTO;
import pe.edu.ulima.espacioya.administracion.dto.PaginaReclamosDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReclamablesDTO;
import pe.edu.ulima.espacioya.administracion.service.ReclamoService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;

/**
 * Reclamos de las partes (HU-26). La identidad sale de la sesión; el rol lo comprueba el servicio. No hay rutas para
 * editar ni borrar (R30): Spring responde 405.
 */
@RestController
@RequestMapping("/api/reclamos")
public class ReclamoController {

    private final ReclamoService reclamos;

    public ReclamoController(ReclamoService reclamos) {
        this.reclamos = reclamos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DetalleReclamoDTO abrir(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                   @Valid @RequestBody CrearReclamoDTO datos, HttpServletRequest peticion) {
        return reclamos.abrir(usuario, datos, IpCliente.de(peticion));
    }

    @GetMapping("/reclamables")
    public ResponseEntity<ReclamablesDTO> reclamables(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reclamos.reclamables(usuario));
    }

    @GetMapping
    public ResponseEntity<PaginaReclamosDTO> listar(@AuthenticationPrincipal UsuarioAutenticado parte,
                                                    @RequestParam(required = false) String pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reclamos.listar(parte, pagina));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetalleReclamoDTO> detalle(@AuthenticationPrincipal UsuarioAutenticado parte,
                                                     @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reclamos.detalle(parte, ConversionReclamos.idReclamo(id)));
    }

    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeReclamoDTO enviarMensaje(@AuthenticationPrincipal UsuarioAutenticado parte, @PathVariable String id,
                                           @Valid @RequestBody NuevoMensajeDTO datos, HttpServletRequest peticion) {
        return reclamos.enviarMensaje(parte, ConversionReclamos.idReclamo(id), datos, IpCliente.de(peticion));
    }
}
