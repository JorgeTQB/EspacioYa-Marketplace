package pe.edu.ulima.espacioya.pagos.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.pagos.dto.ReembolsoAdminDTO;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoAdminService;

/**
 * Reembolsos por atender y su reintento (HU-16 CA2). Antes de llegar aquí, {@code FiltroIpsAdmin} exige una IP
 * permitida y el filtro de sesión una sesión vigente; el rol {@code ADMIN} lo comprueba el servicio. El reintento no
 * lee ningún cuerpo: monto y transacción salen de la base (R35).
 */
@RestController
@RequestMapping("/api/admin/reembolsos")
public class ReembolsoAdminController {

    private final ReembolsoAdminService servicio;

    public ReembolsoAdminController(ReembolsoAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<ReembolsoAdminDTO>> porAtender(@AuthenticationPrincipal UsuarioAutenticado admin) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.porAtender(admin));
    }

    // 200 y no 201: responde el estado del reembolso, no crea un recurso (DT-8).
    @PostMapping("/{id}/reintento")
    public ResponseEntity<ReembolsoAdminDTO> reintentar(@AuthenticationPrincipal UsuarioAutenticado admin,
                                                        @PathVariable String id, HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.reintentar(admin, id, IpCliente.de(peticion)));
    }
}
