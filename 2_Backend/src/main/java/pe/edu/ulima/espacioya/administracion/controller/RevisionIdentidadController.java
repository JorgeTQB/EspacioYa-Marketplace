package pe.edu.ulima.espacioya.administracion.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.administracion.dto.MotivoRechazoDTO;
import pe.edu.ulima.espacioya.administracion.dto.RevisionIdentidadDTO;
import pe.edu.ulima.espacioya.administracion.service.RevisionIdentidadService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ExpedienteIdentidadDTO;

/**
 * Panel admin de revisión de identidad (HU-06). Antes de llegar aquí, {@code FiltroIpsAdmin} exige una IP permitida
 * y el filtro de sesión una sesión vigente; el rol {@code ADMIN} lo comprueba el servicio. El {@code id} de la ruta es
 * el arrendador revisado: quien decide sale siempre de la sesión.
 */
@RestController
@RequestMapping("/api/admin/arrendadores")
public class RevisionIdentidadController {

    private final RevisionIdentidadService servicio;

    public RevisionIdentidadController(RevisionIdentidadService servicio) {
        this.servicio = servicio;
    }

    // Datos personales y URL firmadas: ningún intermediario ni el navegador deben guardarlos (R26).
    @GetMapping("/en-revision")
    public ResponseEntity<List<ArrendadorEnRevisionDTO>> cola(@AuthenticationPrincipal UsuarioAutenticado admin) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.cola(admin));
    }

    @GetMapping("/{id}/identidad")
    public ResponseEntity<ExpedienteIdentidadDTO> expediente(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                             @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.expediente(admin, id));
    }

    @PostMapping("/{id}/identidad/aprobacion")
    @ResponseStatus(HttpStatus.CREATED)
    public RevisionIdentidadDTO aprobar(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable Long id,
                                        HttpServletRequest peticion) {
        return servicio.aprobar(admin, id, IpCliente.de(peticion));
    }

    @PostMapping("/{id}/identidad/rechazo")
    @ResponseStatus(HttpStatus.CREATED)
    public RevisionIdentidadDTO rechazar(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable Long id,
                                         @Valid @RequestBody MotivoRechazoDTO cuerpo, HttpServletRequest peticion) {
        return servicio.rechazar(admin, id, cuerpo.motivo(), IpCliente.de(peticion));
    }
}
