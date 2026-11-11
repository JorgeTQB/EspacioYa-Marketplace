package pe.edu.ulima.espacioya.administracion.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.administracion.dto.CrearReporteDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReporteCreadoDTO;
import pe.edu.ulima.espacioya.administracion.service.ReporteService;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;

/**
 * Reportar un espacio o una reseña (HU-27 CA1). La identidad sale de la sesión; el rol lo comprueba el servicio
 * después de validar el cuerpo (DT-15). No hay rutas para editar ni borrar un reporte (R55): Spring responde 405.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reportes;

    public ReporteController(ReporteService reportes) {
        this.reportes = reportes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReporteCreadoDTO reportar(@AuthenticationPrincipal UsuarioAutenticado reportante,
                                     @Valid @RequestBody CrearReporteDTO datos, HttpServletRequest peticion) {
        return reportes.reportar(reportante, datos, IpCliente.de(peticion));
    }
}
