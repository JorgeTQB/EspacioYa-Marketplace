package pe.edu.ulima.espacioya.comun.almacenamiento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;

/**
 * Sirve un archivo privado a quien tenga una URL firmada vigente (HU-05, RNF-SEG-15). Es público: la
 * firma es la autorización, porque el navegador abre el enlace sin el encabezado Authorization.
 * Cualquier fallo responde el mismo 404, sin revelar si la clave existe.
 */
@RestController
public class ArchivoController {

    private static final Logger log = LoggerFactory.getLogger(ArchivoController.class);

    private final FirmadorUrl firmador;
    private final ArchivosPrivados archivos;

    public ArchivoController(FirmadorUrl firmador, ArchivosPrivados archivos) {
        this.firmador = firmador;
        this.archivos = archivos;
    }

    @GetMapping(FirmadorUrl.RUTA)
    public ResponseEntity<byte[]> servir(@RequestParam(required = false) String clave,
                                         @RequestParam(required = false) String tipo,
                                         @RequestParam(required = false) Long expira,
                                         @RequestParam(required = false) String firma) {
        if (clave == null || expira == null || !firmador.esValida(clave, tipo, expira, firma)) {
            throw new RecursoNoEncontradoException();
        }
        var tipoArchivo = TipoArchivo.deMime(tipo).orElseThrow(RecursoNoEncontradoException::new);
        byte[] contenido;
        try {
            contenido = archivos.leer(clave).orElseThrow(RecursoNoEncontradoException::new);
        } catch (IllegalStateException e) {
            log.warn("Un archivo privado con firma válida no se pudo descifrar");
            throw new RecursoNoEncontradoException();
        }
        var disposicion = "inline; filename=\"documento." + tipoArchivo.extension() + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipoArchivo.mime()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion)
                .body(contenido);
    }
}
