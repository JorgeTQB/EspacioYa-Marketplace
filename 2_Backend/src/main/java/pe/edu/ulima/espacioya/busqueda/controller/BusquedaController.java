package pe.edu.ulima.espacioya.busqueda.controller;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.busqueda.dto.ResultadoBusquedaDTO;
import pe.edu.ulima.espacioya.busqueda.service.BusquedaService;
import pe.edu.ulima.espacioya.busqueda.service.ConsultaBusqueda;

@RestController
public class BusquedaController {

    private static final String PARAMETRO_CARACTERISTICA = "caracteristica";

    private final BusquedaService busquedaService;

    public BusquedaController(BusquedaService busquedaService) {
        this.busquedaService = busquedaService;
    }

    // Los parámetros llegan como texto: el servicio responde 400 por campo (HU-10 DT-10). Los demás se ignoran.
    // HU-23 DT-6: caracteristica se lee cruda con getParameterValues; @RequestParam List partiría "A,B" en dos.
    @GetMapping(ReglasLimiteBusqueda.RUTA_BUSQUEDA)
    public ResultadoBusquedaDTO buscar(@RequestParam(required = false) String tipo,
                                       @RequestParam(required = false) String zona,
                                       @RequestParam(required = false) String fecha,
                                       @RequestParam(required = false) String pagina,
                                       @RequestParam(required = false) String precioMaximo,
                                       @RequestParam(required = false) String capacidadMinima,
                                       HttpServletRequest solicitud) {
        var valores = solicitud.getParameterValues(PARAMETRO_CARACTERISTICA);
        return busquedaService.buscar(new ConsultaBusqueda(tipo, zona, fecha, pagina, precioMaximo, capacidadMinima,
                valores == null ? List.of() : Arrays.asList(valores)));
    }
}
