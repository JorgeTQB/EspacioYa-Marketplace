package pe.edu.ulima.espacioya.cuentas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.ArchivoMultipart;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.DatosPerfilDTO;
import pe.edu.ulima.espacioya.cuentas.dto.MensajeDTO;
import pe.edu.ulima.espacioya.cuentas.dto.PerfilDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SolicitudCambioCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.service.CambioCorreoService;
import pe.edu.ulima.espacioya.cuentas.service.PerfilService;

/**
 * Perfil del titular de la sesión (HU-20). Ninguna ruta lleva un id: la cuenta sale de la sesión (R4); el rol lo
 * comprueba el servicio (R2). Toda respuesta lleva {@code no-store} (R7).
 */
@RestController
@RequestMapping("/api/usuarios/actual")
public class PerfilController {

    private final PerfilService perfiles;
    private final CambioCorreoService cambiosCorreo;

    public PerfilController(PerfilService perfiles, CambioCorreoService cambiosCorreo) {
        this.perfiles = perfiles;
        this.cambiosCorreo = cambiosCorreo;
    }

    @GetMapping("/perfil")
    public ResponseEntity<PerfilDTO> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return sinCache(perfiles.consultar(usuario));
    }

    @PutMapping("/perfil")
    public ResponseEntity<PerfilDTO> actualizarDatos(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                     @Valid @RequestBody DatosPerfilDTO datos,
                                                     HttpServletRequest peticion) {
        return sinCache(perfiles.actualizarDatos(usuario, datos, IpCliente.de(peticion)));
    }

    @PutMapping(path = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PerfilDTO> subirFoto(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                               @RequestPart(name = "foto", required = false) MultipartFile foto,
                                               HttpServletRequest peticion) {
        return sinCache(perfiles.guardarFoto(usuario, ArchivoMultipart.recibido(foto), IpCliente.de(peticion)));
    }

    // Con el tipo real guardado y nosniff: el navegador no la interpreta como otra cosa (como las fotos de HU-07).
    @GetMapping("/foto")
    public ResponseEntity<byte[]> verFoto(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        var foto = perfiles.foto(usuario);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(foto.tipoMime()))
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.contenido());
    }

    @DeleteMapping("/foto")
    public ResponseEntity<PerfilDTO> quitarFoto(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                HttpServletRequest peticion) {
        return sinCache(perfiles.quitarFoto(usuario, IpCliente.de(peticion)));
    }

    // 202 y el mismo mensaje esté o no libre el correo: la respuesta no debe revelarlo (R29).
    @PostMapping("/cambios-correo")
    public ResponseEntity<MensajeDTO> solicitarCambioCorreo(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                            @Valid @RequestBody SolicitudCambioCorreoDTO solicitud,
                                                            HttpServletRequest peticion) {
        cambiosCorreo.solicitar(usuario, solicitud, IpCliente.de(peticion));
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(new MensajeDTO(CambioCorreoService.MENSAJE_SOLICITUD_RECIBIDA));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
