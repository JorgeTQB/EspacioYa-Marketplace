package pe.edu.ulima.espacioya.pagos.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.dto.MiLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.PaginaLiquidacionesDTO;
import pe.edu.ulima.espacioya.pagos.service.MisLiquidacionesService;

/**
 * "Mis liquidaciones" del arrendador de la sesión (HU-19 CA2, CA3). La ruta no lleva id de arrendador (R6); el rol lo
 * comprueba el servicio. La respuesta lleva {@code no-store} (R41).
 */
@RestController
@RequestMapping("/api/arrendadores/actual/liquidaciones")
public class MisLiquidacionesController {

    private final MisLiquidacionesService servicio;

    public MisLiquidacionesController(MisLiquidacionesService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<PaginaLiquidacionesDTO<MiLiquidacionDTO>> listar(
            @AuthenticationPrincipal UsuarioAutenticado arrendador, @RequestParam(required = false) String pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(arrendador, pagina));
    }
}
