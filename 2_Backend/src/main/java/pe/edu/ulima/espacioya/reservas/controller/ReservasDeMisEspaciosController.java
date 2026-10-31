package pe.edu.ulima.espacioya.reservas.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.reservas.dto.PaginaReservasDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDeMiEspacioDTO;
import pe.edu.ulima.espacioya.reservas.service.ConsultaReservasService;

/** "Reservas de mis espacios" (HU-14 CA2): el arrendador sale del token, nunca de un parámetro (R7). */
@RestController
@RequestMapping("/api/arrendadores/actual/reservas")
public class ReservasDeMisEspaciosController {

    private final ConsultaReservasService consulta;

    public ReservasDeMisEspaciosController(ConsultaReservasService consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    public PaginaReservasDTO<ReservaDeMiEspacioDTO> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                           @RequestParam(required = false) String grupo,
                                                           @RequestParam(required = false) String pagina) {
        return consulta.reservasDeMisEspacios(usuario, grupo, pagina);
    }
}
