package pe.edu.ulima.espacioya.pagos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.pagos.domain.CheckoutPago;
import pe.edu.ulima.espacioya.pagos.repository.CheckoutPagoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.SolicitudCheckout;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;

/** Un enlace de pago por reserva (HU-13 DT-3). El HTTP va sin transacción: ninguna llamada externa retiene filas. */
@Service
public class SesionesCheckout {

    private static final String RESTRICCION_UN_CHECKOUT = "uq_checkout_pago_reserva";

    private final PasarelaPago pasarela;
    private final CheckoutPagoRepository checkouts;
    private final TransactionTemplate transacciones;
    private final Clock reloj;
    private final Duration margenCierre;
    private final String urlFrontend;

    public SesionesCheckout(PasarelaPago pasarela, CheckoutPagoRepository checkouts, TransactionTemplate transacciones,
                            Clock reloj, @Value("${espacioya.pagos.margen-cierre}") Duration margenCierre,
                            @Value("${espacioya.frontend.url}") String urlFrontend) {
        this.pasarela = pasarela;
        this.checkouts = checkouts;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.margenCierre = margenCierre;
        this.urlFrontend = urlFrontend;
    }

    public String urlPara(ReservaPorCobrar reserva) {
        return checkouts.findByReservaId(reserva.id())
                .map(CheckoutPago::getUrlPago)
                .orElseGet(() -> crear(reserva));
    }

    public boolean existe(Long reservaId) {
        return checkouts.existsByReservaId(reservaId);
    }

    public void cerrar(Long reservaId) {
        checkouts.findByReservaId(reservaId)
                .ifPresent(checkout -> pasarela.cerrarCheckout(checkout.getIdCheckout()));
    }

    private String crear(ReservaPorCobrar reserva) {
        var creado = pasarela.crearCheckout(new SolicitudCheckout(reserva.id(), reserva.montoTotal(),
                reserva.expiraEn().minus(margenCierre), urlFrontend + "/reservas/" + reserva.id()));
        var checkout = new CheckoutPago(reserva.id(), creado.idCheckout(), creado.urlPago(), Instant.now(reloj));
        try {
            transacciones.executeWithoutResult(estado -> checkouts.saveAndFlush(checkout));
            return creado.urlPago();
        } catch (DataIntegrityViolationException conflicto) {
            if (!esCheckoutYaCreado(conflicto)) {
                throw conflicto;
            }
            return checkouts.findByReservaId(reserva.id()).orElseThrow(() -> conflicto).getUrlPago();
        }
    }

    // Por el nombre de la restricción: otro fallo de integridad no debe disfrazarse de carrera ganada.
    private static boolean esCheckoutYaCreado(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(RESTRICCION_UN_CHECKOUT);
    }
}
