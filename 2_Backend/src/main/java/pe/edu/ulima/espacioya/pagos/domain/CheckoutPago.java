package pe.edu.ulima.espacioya.pagos.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Enlace de pago de una reserva (HU-13 DT-3): uno por reserva, para no dejar dos cobros abiertos. */
@Entity
@Table(name = "checkout_pago")
public class CheckoutPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "id_checkout", nullable = false, length = 100)
    private String idCheckout;

    @Column(name = "url_pago", nullable = false)
    private String urlPago;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected CheckoutPago() {
    }

    public CheckoutPago(Long reservaId, String idCheckout, String urlPago, Instant creadoEn) {
        this.reservaId = reservaId;
        this.idCheckout = idCheckout;
        this.urlPago = urlPago;
        this.creadoEn = creadoEn;
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public String getIdCheckout() {
        return idCheckout;
    }

    public String getUrlPago() {
        return urlPago;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
