package pe.edu.ulima.espacioya.pagos.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.pagos.dto.SimulacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.SimularPagoDTO;
import pe.edu.ulima.espacioya.pagos.service.SimulacionPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ResultadoPasarela;

/** Solo existe con la pasarela simulada (HU-13 D9): con {@code mercadopago} esta ruta responde 404. */
@RestController
@RequestMapping("/api/reservas/{id}/pago/simulacion")
@ConditionalOnProperty(name = "espacioya.pagos.pasarela", havingValue = "simulada")
public class PagoSimuladoController {

    private final SimulacionPago simulacion;

    public PagoSimuladoController(SimulacionPago simulacion) {
        this.simulacion = simulacion;
    }

    @GetMapping
    public SimulacionDTO consultar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String id) {
        return simulacion.consultar(usuario, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void simular(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String id,
                        @Valid @RequestBody SimularPagoDTO datos, HttpServletRequest peticion) {
        simulacion.simular(usuario, id, ResultadoPasarela.valueOf(datos.resultado()), IpCliente.de(peticion));
    }
}
