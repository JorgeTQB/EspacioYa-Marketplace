package pe.edu.ulima.espacioya.pagos.service.reembolso;

import java.time.Clock;
import java.time.Instant;

import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ReembolsoRealizado;
import pe.edu.ulima.espacioya.pagos.service.pasarela.SolicitudReembolsoPasarela;

/**
 * ConcreteCommand (★ HU-16 DT-2): un intento del reembolso {@code reembolsoId}, pedido por {@code solicitadoPor} desde
 * {@code ip}, contra el Receiver {@link PasarelaPago}. Lo arma {@link SolicitudesReembolso}.
 * <p>
 * Idempotente: si el reembolso ya está {@code REEMBOLSADO} o en proceso, no llama a la pasarela. Reintentable: uno
 * {@code FALLIDO} o sin respuesta se vuelve a ejecutar con la misma clave de idempotencia. Ninguna fila queda bloqueada
 * durante la llamada externa (DT-5).
 */
public final class SolicitudReembolso implements ComandoReembolso {

    private final Long reembolsoId;
    private final Disparo disparo;
    private final Long solicitadoPor;
    private final String ip;
    private final PasarelaPago pasarela;
    private final RegistroReembolsos registro;
    private final Clock reloj;

    SolicitudReembolso(Long reembolsoId, Disparo disparo, Long solicitadoPor, String ip, PasarelaPago pasarela,
                       RegistroReembolsos registro, Clock reloj) {
        this.reembolsoId = reembolsoId;
        this.disparo = disparo;
        this.solicitadoPor = solicitadoPor;
        this.ip = ip;
        this.pasarela = pasarela;
        this.registro = registro;
        this.reloj = reloj;
    }

    @Override
    public ResultadoReembolso ejecutar() {
        var reclamado = registro.iniciar(reembolsoId, Instant.now(reloj));
        if (reclamado.isEmpty()) {
            return ResultadoReembolso.NO_EJECUTADO;
        }
        var intento = reclamado.get();
        ReembolsoRealizado realizado;
        try {
            realizado = pasarela.reembolsar(new SolicitudReembolsoPasarela(intento.idTransaccion(), intento.monto(),
                    intento.claveIdempotencia(), intento.numero()));
        } catch (RuntimeException e) {
            registro.registrarFallo(intento, this);
            return ResultadoReembolso.FALLIDO;
        }
        registro.registrarExito(intento, realizado, this);
        return ResultadoReembolso.REEMBOLSADO;
    }

    public Long reembolsoId() {
        return reembolsoId;
    }

    public Disparo disparo() {
        return disparo;
    }

    public Long solicitadoPor() {
        return solicitadoPor;
    }

    public String ip() {
        return ip;
    }
}
