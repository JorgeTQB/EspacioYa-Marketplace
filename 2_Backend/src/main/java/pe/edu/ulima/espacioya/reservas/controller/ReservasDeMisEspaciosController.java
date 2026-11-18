package pe.edu.ulima.espacioya.reservas.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.dto.CancelacionDTO;
import pe.edu.ulima.espacioya.reservas.dto.ConfirmarCancelacionDTO;
import pe.edu.ulima.espacioya.reservas.dto.PaginaReservasDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDeMiEspacioDTO;
import pe.edu.ulima.espacioya.reservas.dto.VistaCancelacionDTO;
import pe.edu.ulima.espacioya.reservas.service.CancelacionReservaService;
import pe.edu.ulima.espacioya.reservas.service.ConsultaReservasService;

/** "Reservas de mis espacios" (HU-14 CA2, HU-15): el arrendador sale del token, nunca de un parámetro (R7). */
@RestController
@RequestMapping("/api/arrendadores/actual/reservas")
public class ReservasDeMisEspaciosController {

    private final ConsultaReservasService consulta;
    private final CancelacionReservaService cancelacion;

    public ReservasDeMisEspaciosController(ConsultaReservasService consulta, CancelacionReservaService cancelacion) {
        this.consulta = consulta;
        this.cancelacion = cancelacion;
    }

    @GetMapping
    public PaginaReservasDTO<ReservaDeMiEspacioDTO> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                           @RequestParam(required = false) String grupo,
                                                           @RequestParam(required = false) String pagina,
                                                           @RequestParam(required = false) String espacioId) {
        return consulta.reservasDeMisEspacios(usuario, grupo, pagina, espacioId);
    }

    // HU-15: el arrendador cancela una reserva de su espacio; siempre con el 100 % para el usuario (R13).
    @GetMapping("/{id}/cancelacion")
    public VistaCancelacionDTO vistaPreviaCancelacion(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                      @PathVariable String id) {
        return cancelacion.vistaPrevia(usuario, id, QuienCancela.ARRENDADOR);
    }

    // Sin @Valid: el servicio comprueba rol, id y monto visto en ese orden (DT-6).
    @PostMapping("/{id}/cancelacion")
    public CancelacionDTO cancelar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String id,
                                   @RequestBody(required = false) ConfirmarCancelacionDTO datos,
                                   HttpServletRequest peticion) {
        return cancelacion.confirmar(usuario, id, QuienCancela.ARRENDADOR, datos, IpCliente.de(peticion));
    }
}
