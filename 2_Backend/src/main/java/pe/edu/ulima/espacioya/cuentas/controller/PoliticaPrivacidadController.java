package pe.edu.ulima.espacioya.cuentas.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.cuentas.dto.VersionPoliticaDTO;
import pe.edu.ulima.espacioya.cuentas.service.PoliticaPrivacidad;

@RestController
@RequestMapping("/api/politicas-privacidad")
public class PoliticaPrivacidadController {

    private final PoliticaPrivacidad politica;

    public PoliticaPrivacidadController(PoliticaPrivacidad politica) {
        this.politica = politica;
    }

    @GetMapping("/vigente")
    public VersionPoliticaDTO vigente() {
        return new VersionPoliticaDTO(politica.versionVigente());
    }
}
