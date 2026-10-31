package pe.edu.ulima.espacioya.reservas.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.reservas.dto.CrearReservaDTO;
import pe.edu.ulima.espacioya.reservas.dto.PaginaReservasDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;
import pe.edu.ulima.espacioya.reservas.service.ConsultaReservasService;
import pe.edu.ulima.espacioya.reservas.service.ReservaService;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservas;
    private final ConsultaReservasService consulta;

    public ReservaController(ReservaService reservas, ConsultaReservasService consulta) {
        this.reservas = reservas;
        this.consulta = consulta;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaDTO crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
                            @Valid @RequestBody CrearReservaDTO datos, HttpServletRequest peticion) {
        return reservas.crear(usuario, datos, IpCliente.de(peticion));
    }

    // "Mis reservas" (HU-14): la identidad sale del token; cualquier otro parámetro se ignora (R7). Los dos llegan como
    // texto para que el servicio valide grupo y página juntos (R31).
    @GetMapping
    public PaginaReservasDTO<ReservaDTO> misReservas(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                     @RequestParam(required = false) String grupo,
                                                     @RequestParam(required = false) String pagina) {
        return consulta.misReservas(usuario, grupo, pagina);
    }

    // El id llega como texto: un id mal escrito es el mismo 404 que uno ajeno o inexistente (DT-9).
    @GetMapping("/{id}")
    public ReservaDTO consultar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String id) {
        return reservas.consultar(usuario, id);
    }
}
