package pe.edu.ulima.espacioya.pagos.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.pagos.dto.CheckoutDTO;
import pe.edu.ulima.espacioya.pagos.dto.EstadoPagoDTO;
import pe.edu.ulima.espacioya.pagos.service.CheckoutFacade;

/** Sin {@code @RequestBody}: el monto sale de la reserva y el cuerpo del cliente se ignora (HU-13 R3). */
@RestController
@RequestMapping("/api/reservas/{id}/pago")
public class PagoController {

    private final CheckoutFacade checkout;

    public PagoController(CheckoutFacade checkout) {
        this.checkout = checkout;
    }

    // El id llega como texto: uno mal escrito es el mismo 404 que uno ajeno o inexistente (HU-12 DT-9).
    @PostMapping
    public CheckoutDTO iniciar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String id,
                               HttpServletRequest peticion) {
        return checkout.iniciar(usuario, id, IpCliente.de(peticion));
    }

    @GetMapping
    public EstadoPagoDTO estado(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String id) {
        return checkout.estado(usuario, id);
    }
}
