package pe.edu.ulima.espacioya.administracion.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.administracion.dto.DetalleReclamoAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.PaginaReclamosDTO;
import pe.edu.ulima.espacioya.administracion.dto.ResolucionReclamoDTO;
import pe.edu.ulima.espacioya.administracion.service.ReclamoAdminService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;

/**
 * Panel del admin para resolver reclamos (HU-26 CA2, CA3). {@code FiltroIpsAdmin} exige una IP permitida; el rol
 * {@code ADMIN} lo comprueba el servicio antes de leer.
 */
@RestController
@RequestMapping("/api/admin/reclamos")
public class ReclamoAdminController {

    private final ReclamoAdminService servicio;

    public ReclamoAdminController(ReclamoAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<PaginaReclamosDTO> listar(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                    @RequestParam(required = false) String grupo,
                                                    @RequestParam(required = false) String pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(admin, grupo, pagina));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetalleReclamoAdminDTO> detalle(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                          @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.detalle(admin, ConversionReclamos.idReclamo(id)));
    }

    @PostMapping("/{id}/resolucion")
    public ResponseEntity<DetalleReclamoAdminDTO> resolver(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                           @PathVariable String id,
                                                           @Valid @RequestBody ResolucionReclamoDTO datos,
                                                           HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.resolver(admin, ConversionReclamos.idReclamo(id), datos, IpCliente.de(peticion)));
    }
}
