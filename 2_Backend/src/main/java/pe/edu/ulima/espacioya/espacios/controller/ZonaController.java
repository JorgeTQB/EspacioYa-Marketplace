package pe.edu.ulima.espacioya.espacios.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.espacios.dto.ZonaDTO;
import pe.edu.ulima.espacioya.espacios.service.EspacioService;

@RestController
public class ZonaController {

    private final EspacioService espacioService;

    public ZonaController(EspacioService espacioService) {
        this.espacioService = espacioService;
    }

    @GetMapping("/api/zonas")
    public List<ZonaDTO> listar() {
        return espacioService.listarZonas();
    }
}
