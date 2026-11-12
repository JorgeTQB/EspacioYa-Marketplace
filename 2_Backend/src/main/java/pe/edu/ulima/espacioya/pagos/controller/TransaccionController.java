package pe.edu.ulima.espacioya.pagos.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.dto.PaginaTransaccionesDTO;
import pe.edu.ulima.espacioya.pagos.service.HistorialPagosService;

/**
 * "Mis pagos" y comprobantes del usuario de la sesión (HU-28). Las rutas no llevan id de usuario (R8); el rol y la
 * propiedad los comprueba el servicio. Sin {@code produces} en el comprobante: un error en JSON no debe fallar por
 * negociación de contenido; el tipo PDF se fija solo en el 200. Ambas respuestas llevan {@code no-store} (R21).
 */
@RestController
@RequestMapping("/api/transacciones")
public class TransaccionController {

    private final HistorialPagosService servicio;

    public TransaccionController(HistorialPagosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<PaginaTransaccionesDTO> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                         @RequestParam(required = false) String pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.listar(usuario, pagina));
    }

    @GetMapping("/{id}/comprobante")
    public ResponseEntity<byte[]> comprobante(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                              @PathVariable String id) {
        var comprobante = servicio.comprobante(usuario, id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(comprobante.nombreArchivo()).build().toString())
                .body(comprobante.contenido());
    }
}
