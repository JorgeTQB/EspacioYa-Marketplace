package pe.edu.ulima.espacioya.espacios.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.ArchivoMultipart;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.service.FotoEspacioService;

@RestController
@RequestMapping("/api/arrendadores/actual/espacios/{id}/fotos")
public class FotoEspacioController {

    public static final String PARTE_FOTO = FotoEspacioService.CAMPO_FOTO;

    private final FotoEspacioService fotoService;

    public FotoEspacioController(FotoEspacioService fotoService) {
        this.fotoService = fotoService;
    }

    // La parte es opcional para Spring: si falta, el servicio la reporta como error del campo foto (R28).
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EspacioDTO agregar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                              @RequestPart(name = PARTE_FOTO, required = false) MultipartFile foto,
                              HttpServletRequest peticion) {
        return fotoService.agregar(usuario, id, ArchivoMultipart.recibido(foto), IpCliente.de(peticion));
    }

    @DeleteMapping("/{fotoId}")
    public EspacioDTO eliminar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
                               @PathVariable Long fotoId, HttpServletRequest peticion) {
        return fotoService.eliminar(usuario, id, fotoId, IpCliente.de(peticion));
    }
}
