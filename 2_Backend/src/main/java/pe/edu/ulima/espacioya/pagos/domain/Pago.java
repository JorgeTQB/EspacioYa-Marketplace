package pe.edu.ulima.espacioya.pagos.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de un intento de pago (HU-13). Del medio de pago solo se guardan los últimos 4 dígitos (RNF-CUM-01). */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "id_transaccion_pasarela", nullable = false, length = 100)
    private String idTransaccionPasarela;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(length = 4)
    private String ultimos4;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Pago() {
    }

    public static Pago de(Long reservaId, String idTransaccion, BigDecimal monto, EstadoPago estado, String ultimos4,
                          Instant ahora) {
        var pago = new Pago();
        pago.reservaId = reservaId;
        pago.idTransaccionPasarela = idTransaccion;
        pago.monto = monto;
        pago.estado = estado;
        pago.ultimos4 = ultimos4;
        pago.creadoEn = ahora;
        return pago;
    }

    /** Lo deja solo {@code RegistroReembolsos}, junto con el reembolso (HU-16 D1, RN-07). */
    public void marcarReembolsado() {
        exigirReembolsable();
        estado = EstadoPago.REEMBOLSADO;
    }

    /** Lo deja solo {@code RegistroReembolsos}, junto con el reembolso (HU-16 D1, RN-07). */
    public void marcarReembolsoFallido() {
        exigirReembolsable();
        estado = EstadoPago.REEMBOLSO_FALLIDO;
    }

    private void exigirReembolsable() {
        if (estado != EstadoPago.PAGADO && estado != EstadoPago.REEMBOLSO_FALLIDO) {
            throw new IllegalStateException("El pago " + id + " en " + estado + " no se puede reembolsar");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public String getIdTransaccionPasarela() {
        return idTransaccionPasarela;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public String getUltimos4() {
        return ultimos4;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
