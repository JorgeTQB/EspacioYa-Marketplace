package pe.edu.ulima.espacioya.pagos.service.reembolso;

import java.time.Clock;

import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;

/** Fábrica del ConcreteCommand (HU-16): los Clients arman el comando sin conocer la pasarela ni el registro. */
@Component
public class SolicitudesReembolso {

    private final PasarelaPago pasarela;
    private final RegistroReembolsos registro;
    private final Clock reloj;

    public SolicitudesReembolso(PasarelaPago pasarela, RegistroReembolsos registro, Clock reloj) {
        this.pasarela = pasarela;
        this.registro = registro;
        this.reloj = reloj;
    }

    public SolicitudReembolso crear(Long reembolsoId, Disparo disparo, Long solicitadoPor, String ip) {
        return new SolicitudReembolso(reembolsoId, disparo, solicitadoPor, ip, pasarela, registro, reloj);
    }
}
