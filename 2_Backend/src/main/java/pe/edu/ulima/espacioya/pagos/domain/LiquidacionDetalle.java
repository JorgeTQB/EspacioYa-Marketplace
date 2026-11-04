package pe.edu.ulima.espacioya.pagos.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una reserva liquidada (HU-19): su pago {@code PAGADO} y el monto de la línea. Una reserva y un pago figuran en una
 * sola línea ({@code uq_liquidacion_detalle_reserva}, {@code uq_liquidacion_detalle_pago}). No cambia nunca.
 */
@Entity
@Table(name = "liquidacion_detalle")
public class LiquidacionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "liquidacion_id", nullable = false, updatable = false)
    private Long liquidacionId;

    @Column(name = "reserva_id", nullable = false, updatable = false)
    private Long reservaId;

    @Column(name = "pago_id", nullable = false, updatable = false)
    private Long pagoId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(nullable = false, updatable = false)
    private boolean simulado;

    protected LiquidacionDetalle() {
    }

    public static LiquidacionDetalle de(Long liquidacionId, Long reservaId, Long pagoId, BigDecimal monto,
                                        boolean simulado) {
        var detalle = new LiquidacionDetalle();
        detalle.liquidacionId = liquidacionId;
        detalle.reservaId = reservaId;
        detalle.pagoId = pagoId;
        detalle.monto = monto;
        detalle.simulado = simulado;
        return detalle;
    }

    public Long getId() {
        return id;
    }

    public Long getLiquidacionId() {
        return liquidacionId;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public Long getPagoId() {
        return pagoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public boolean isSimulado() {
        return simulado;
    }
}
