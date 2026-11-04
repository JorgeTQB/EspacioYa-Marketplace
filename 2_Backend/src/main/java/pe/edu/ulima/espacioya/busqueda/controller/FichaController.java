package pe.edu.ulima.espacioya.busqueda.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.busqueda.dto.DisponibilidadPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.dto.FichaPublicaDTO;
import pe.edu.ulima.espacioya.busqueda.service.FichaService;

@RestController
public class FichaController {

    private final FichaService fichaService;

    public FichaController(FichaService fichaService) {
        this.fichaService = fichaService;
    }

    // El id llega como texto: uno mal escrito responde el mismo 404 que un espacio no visible (HU-11 DT-5).
    @GetMapping(ReglasLimiteFicha.RUTA_FICHA)
    public FichaPublicaDTO ficha(@PathVariable String id) {
        return fichaService.ficha(id);
    }

    @GetMapping(ReglasLimiteFicha.RUTA_DISPONIBILIDAD)
    public DisponibilidadPublicaDTO disponibilidad(@PathVariable String id,
                                                   @RequestParam(required = false) String fecha) {
        return fichaService.disponibilidad(id, fecha);
    }
}
