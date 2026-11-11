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

import pe.edu.ulima.espacioya.administracion.dto.DecisionReporteDTO;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReporteAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.PaginaReportesDTO;
import pe.edu.ulima.espacioya.administracion.service.ReporteAdminService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;

/**
 * Panel de moderación del admin (HU-27 CA2, CA3). {@code FiltroIpsAdmin} exige una IP permitida y
 * {@code SesionService} un admin con MFA (R29, R30); el rol {@code ADMIN} lo comprueba el servicio antes de leer. No
 * hay rutas para editar ni borrar un reporte o una decisión (R55): Spring responde 405.
 */
@RestController
@RequestMapping("/api/admin/reportes")
public class ReporteAdminController {

    private final ReporteAdminService servicio;

    public ReporteAdminController(ReporteAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<PaginaReportesDTO> listar(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                    @RequestParam(required = false) String grupo,
                                                    @RequestParam(required = false) String pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(admin, grupo, pagina));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetalleReporteAdminDTO> detalle(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                          @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.detalle(admin, ConversionReportes.idReporte(id)));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<DetalleReporteAdminDTO> decidir(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody DecisionReporteDTO datos,
                                                          HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.decidir(admin, ConversionReportes.idReporte(id), datos, IpCliente.de(peticion)));
    }
}
