package pe.edu.ulima.espacioya.busqueda.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.busqueda.dto.ResultadoBusquedaDTO;
import pe.edu.ulima.espacioya.busqueda.service.BusquedaService;

@RestController
public class BusquedaController {

    private final BusquedaService busquedaService;

    public BusquedaController(BusquedaService busquedaService) {
        this.busquedaService = busquedaService;
    }

    // Los parámetros llegan como texto: el servicio responde 400 por campo (HU-10 DT-10). Los demás se ignoran.
    @GetMapping(ReglasLimiteBusqueda.RUTA_BUSQUEDA)
    public ResultadoBusquedaDTO buscar(@RequestParam(required = false) String tipo,
                                       @RequestParam(required = false) String zona,
                                       @RequestParam(required = false) String fecha,
                                       @RequestParam(required = false) String pagina) {
        return busquedaService.buscar(tipo, zona, fecha, pagina);
    }
}
