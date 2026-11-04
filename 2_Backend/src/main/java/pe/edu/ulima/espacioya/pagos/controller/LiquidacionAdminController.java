package pe.edu.ulima.espacioya.pagos.controller;

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

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.pagos.dto.GeneracionLiquidacionesDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDetalleDTO;
import pe.edu.ulima.espacioya.pagos.dto.MarcarLiquidacionPagadaDTO;
import pe.edu.ulima.espacioya.pagos.dto.PaginaLiquidacionesDTO;
import pe.edu.ulima.espacioya.pagos.service.LiquidacionAdminService;

/**
 * Liquidaciones del admin (HU-19). Antes de llegar aquí, {@code FiltroIpsAdmin} exige una IP permitida y el filtro de
 * sesión una sesión vigente con MFA; el rol {@code ADMIN} lo comprueba el servicio (R1–R3). La generación no declara
 * cuerpo: si llega uno, se ignora (R14). Toda respuesta lleva {@code no-store} (R41).
 */
@RestController
@RequestMapping("/api/admin/liquidaciones")
public class LiquidacionAdminController {

    private final LiquidacionAdminService servicio;

    public LiquidacionAdminController(LiquidacionAdminService servicio) {
        this.servicio = servicio;
    }

    // 200 y no 201: es una acción por lotes que puede no crear nada (S14).
    @PostMapping
    public ResponseEntity<GeneracionLiquidacionesDTO> generar(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                              HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.generar(admin, IpCliente.de(peticion)));
    }

    @GetMapping
    public ResponseEntity<PaginaLiquidacionesDTO<LiquidacionAdminDTO>> listar(
            @AuthenticationPrincipal UsuarioAutenticado admin, @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(admin, grupo, pagina));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LiquidacionAdminDetalleDTO> detalle(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                              @PathVariable String id, HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.detalle(admin, id, IpCliente.de(peticion)));
    }

    // 200 y no 201: cambia el estado de la liquidación, no crea un recurso (como HU-16 DT-8).
    @PostMapping("/{id}/pago")
    public ResponseEntity<LiquidacionAdminDetalleDTO> marcarPagada(
            @AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable String id,
            @Valid @RequestBody MarcarLiquidacionPagadaDTO solicitud, HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.marcarPagada(admin, id, solicitud, IpCliente.de(peticion)));
    }
}
