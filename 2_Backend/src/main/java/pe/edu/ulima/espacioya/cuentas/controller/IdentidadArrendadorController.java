package pe.edu.ulima.espacioya.cuentas.controller;

import java.io.IOException;
import java.io.UncheckedIOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.cuentas.dto.IdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.service.IdentidadArrendadorService;

@RestController
@RequestMapping("/api/arrendadores/actual")
public class IdentidadArrendadorController {

    public static final String PARTE_IDENTIDAD = IdentidadArrendadorService.CAMPO_IDENTIDAD;
    public static final String PARTE_PROPIEDAD = IdentidadArrendadorService.CAMPO_PROPIEDAD;

    private final IdentidadArrendadorService identidadService;

    public IdentidadArrendadorController(IdentidadArrendadorService identidadService) {
        this.identidadService = identidadService;
    }

    // Las partes son opcionales para Spring: si falta una, el servicio la reporta como error de su campo.
    @PostMapping(path = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IdentidadDTO enviar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                               @RequestPart(name = PARTE_IDENTIDAD, required = false) MultipartFile identidad,
                               @RequestPart(name = PARTE_PROPIEDAD, required = false) MultipartFile propiedad,
                               HttpServletRequest peticion) {
        return identidadService.enviar(usuario, recibido(identidad), recibido(propiedad), IpCliente.de(peticion));
    }

    @GetMapping("/identidad")
    public IdentidadDTO consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return identidadService.consultar(usuario);
    }

    // No se leen los bytes de un archivo que ya excede el límite: basta su tamaño para rechazarlo.
    private static ArchivoRecibido recibido(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return new ArchivoRecibido(null, 0);
        }
        if (archivo.getSize() > ValidadorArchivo.TAMANO_MAXIMO) {
            return new ArchivoRecibido(null, archivo.getSize());
        }
        try {
            return new ArchivoRecibido(archivo.getBytes(), archivo.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo recibido", e);
        }
    }
}
