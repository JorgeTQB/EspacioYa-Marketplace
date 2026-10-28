package pe.edu.ulima.espacioya.espacios.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.FotoEspacioService;

/**
 * Sirve las fotos de los espacios en una URL estable (HU-07 DT-4). Es pública: sin sesión, el principal no es un
 * {@link UsuarioAutenticado} y llega {@code null}; el servicio decide la visibilidad por el estado del espacio.
 * El 404 lo arma el manejador global, y su Cache-Control sin caché lo agrega Spring Security.
 */
@RestController
public class FotoPublicaController {

    // Una hora acota cuánto puede seguir en caché la foto de un espacio que dejó de estar publicado.
    public static final String CACHE_PUBLICA = "public, max-age=3600";
    public static final String CACHE_PRIVADA = "no-store";

    private final FotoEspacioService fotoService;

    public FotoPublicaController(FotoEspacioService fotoService) {
        this.fotoService = fotoService;
    }

    @GetMapping("/api/fotos/{uuid}")
    public ResponseEntity<byte[]> ver(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String uuid) {
        var foto = fotoService.ver(usuario, uuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoMime()))
                .header(HttpHeaders.CACHE_CONTROL, foto.publica() ? CACHE_PUBLICA : CACHE_PRIVADA)
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.contenido());
    }
}
