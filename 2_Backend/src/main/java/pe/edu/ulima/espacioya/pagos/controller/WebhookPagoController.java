package pe.edu.ulima.espacioya.pagos.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.ulima.espacioya.comun.web.IpCliente;
import pe.edu.ulima.espacioya.pagos.service.WebhookPagoService;
import pe.edu.ulima.espacioya.pagos.service.pasarela.FirmaInvalidaException;
import pe.edu.ulima.espacioya.pagos.service.pasarela.NotificacionRecibida;

/**
 * Ruta pública para la pasarela. No lee el cuerpo: la firma no lo cubre (HU-13 DT-14). Responde 200 a todo lo que no
 * deba reintentarse, 401 sin cuerpo a una firma inválida y 503, por el manejador global, si la pasarela no responde.
 */
@RestController
public class WebhookPagoController {

    private static final Logger log = LoggerFactory.getLogger(WebhookPagoController.class);

    private final WebhookPagoService webhook;

    public WebhookPagoController(WebhookPagoService webhook) {
        this.webhook = webhook;
    }

    @PostMapping("/api/pagos/webhook")
    public ResponseEntity<Void> recibir(@RequestParam Map<String, String> parametros,
                                        @RequestHeader Map<String, String> encabezados, HttpServletRequest peticion) {
        try {
            webhook.recibir(new NotificacionRecibida(parametros, encabezados), IpCliente.de(peticion));
            return ResponseEntity.ok().build();
        } catch (FirmaInvalidaException e) {
            // Solo al log y sin datos (S9): la ruta es pública y una inundación llenaría la auditoría.
            log.warn("Notificación de pago rechazada por firma inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
