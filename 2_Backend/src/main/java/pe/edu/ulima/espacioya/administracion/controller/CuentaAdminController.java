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

import pe.edu.ulima.espacioya.administracion.dto.AccionCuentaDTO;
import pe.edu.ulima.espacioya.administracion.dto.CuentaAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.MotivoSuspensionDTO;
import pe.edu.ulima.espacioya.administracion.service.CuentaAdminService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.BusquedaCuentasDTO;

/**
 * Panel admin de suspensión y reactivación de cuentas (HU-09). Antes de llegar aquí, {@code FiltroIpsAdmin} exige una
 * IP permitida y el filtro de sesión una sesión vigente; el rol {@code ADMIN} lo comprueba el servicio. El {@code id}
 * de la ruta es la cuenta gestionada: quien actúa sale siempre de la sesión.
 */
@RestController
@RequestMapping("/api/admin/cuentas")
public class CuentaAdminController {

    private final CuentaAdminService servicio;

    public CuentaAdminController(CuentaAdminService servicio) {
        this.servicio = servicio;
    }

    // Datos personales: ningún intermediario ni el navegador deben guardarlos (R13). Sin q, el servicio responde el
    // error del campo q (R6).
    @GetMapping
    public ResponseEntity<BusquedaCuentasDTO> buscar(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                     @RequestParam(required = false) String q) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.buscar(admin, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuentaAdminDTO> detalle(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.detalle(admin, id));
    }

    @PostMapping("/{id}/suspension")
    @ResponseStatus(HttpStatus.CREATED)
    public AccionCuentaDTO suspender(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable Long id,
                                     @Valid @RequestBody MotivoSuspensionDTO cuerpo, HttpServletRequest peticion) {
        return servicio.suspender(admin, id, cuerpo.motivo(), IpCliente.de(peticion));
    }

    @PostMapping("/{id}/reactivacion")
    @ResponseStatus(HttpStatus.CREATED)
    public AccionCuentaDTO reactivar(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable Long id,
                                     HttpServletRequest peticion) {
        return servicio.reactivar(admin, id, IpCliente.de(peticion));
    }
}
