package pe.edu.ulima.espacioya.cuentas.controller;

import java.time.LocalDate;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.ConfirmacionContrasenaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ExportacionDatosDTO;
import pe.edu.ulima.espacioya.cuentas.dto.MensajeDTO;
import pe.edu.ulima.espacioya.cuentas.service.DatosPersonalesService;

/**
 * Derechos sobre datos personales del titular de la sesión (HU-22). {@code POST} con la contraseña en el cuerpo:
 * nunca en una URL (DT-12). Ninguna ruta lleva un id; toda respuesta 200 lleva {@code no-store} (R11).
 */
@RestController
@RequestMapping("/api/usuarios/actual")
public class DatosPersonalesController {

    static final String PREFIJO_ARCHIVO = "espacioya-mis-datos-";

    private final DatosPersonalesService datosPersonales;

    public DatosPersonalesController(DatosPersonalesService datosPersonales) {
        this.datosPersonales = datosPersonales;
    }

    @PostMapping("/exportaciones")
    public ResponseEntity<ExportacionDatosDTO> exportar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                        @RequestBody ConfirmacionContrasenaDTO confirmacion,
                                                        HttpServletRequest peticion) {
        var exportacion = datosPersonales.exportar(usuario, confirmacion.contrasena(), IpCliente.de(peticion));
        // La fecha del nombre es la de Lima del mismo instante que generadaEn (R10).
        var fecha = LocalDate.ofInstant(exportacion.generadaEn(), ZonaHoraria.LIMA);
        var adjunto = ContentDisposition.attachment().filename(PREFIJO_ARCHIVO + fecha + ".json").build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, adjunto.toString())
                .body(exportacion);
    }

    @PostMapping("/eliminacion")
    public ResponseEntity<MensajeDTO> eliminar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                               @RequestBody ConfirmacionContrasenaDTO confirmacion,
                                               HttpServletRequest peticion) {
        datosPersonales.eliminar(usuario, confirmacion.contrasena(), IpCliente.de(peticion));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MensajeDTO(DatosPersonalesService.MENSAJE_ELIMINADA));
    }
}
