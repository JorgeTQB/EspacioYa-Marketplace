package pe.edu.ulima.espacioya.pagos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;

/**
 * Liquidación de un arrendador (HU-19): las reservas liquidables a un corte, con bruto, comisión del 10 % y neto
 * calculados aquí (STRIDE T). Nace {@code GENERADA} o {@code PENDIENTE_CUENTA} y pasa a {@code PAGADA}, que es final:
 * sus montos y líneas no cambian nunca (R17, S8).
 */
@Entity
@Table(name = "liquidacion")
public class Liquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arrendador_id", nullable = false, updatable = false)
    private Long arrendadorId;

    @Column(nullable = false, updatable = false)
    private LocalDate periodo;

    @Column(name = "monto_bruto", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal montoBruto;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal comision;

    @Column(name = "monto_neto", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal montoNeto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoLiquidacion estado;

    @Column(nullable = false, updatable = false)
    private boolean simulada;

    @Column(name = "generada_en", nullable = false, updatable = false)
    private Instant generadaEn;

    @Column(name = "generada_por", nullable = false, updatable = false)
    private Long generadaPor;

    @Column(name = "numero_operacion", length = 30)
    private String numeroOperacion;

    @Column(name = "pagada_en")
    private Instant pagadaEn;

    @Column(name = "pagada_por")
    private Long pagadaPor;

    @Column(name = "banco_destino", length = 60)
    private String bancoDestino;

    @Column(name = "cuenta_destino", length = 8)
    private String cuentaDestino;

    protected Liquidacion() {
    }

    /**
     * Montos con {@link ReglasLiquidacion}; {@code GENERADA} si el arrendador tiene cuenta (retenida o no, R15), si no
     * {@code PENDIENTE_CUENTA} (R16); periodo = fecha del corte en Lima (D1).
     *
     * @throws IllegalArgumentException si no hay líneas.
     */
    public static Liquidacion generar(Long arrendadorId, List<BigDecimal> montosDeLineas, boolean conCuenta,
                                      boolean simulada, Instant corte, Long adminId) {
        if (montosDeLineas.isEmpty()) {
            throw new IllegalArgumentException("Una liquidación necesita al menos una reserva");
        }
        var liquidacion = new Liquidacion();
        liquidacion.arrendadorId = arrendadorId;
        liquidacion.periodo = LocalDate.ofInstant(corte, ZonaHoraria.LIMA);
        liquidacion.montoBruto = montosDeLineas.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        liquidacion.comision = ReglasLiquidacion.comision(liquidacion.montoBruto);
        liquidacion.montoNeto = liquidacion.montoBruto.subtract(liquidacion.comision);
        liquidacion.estado = conCuenta ? EstadoLiquidacion.GENERADA : EstadoLiquidacion.PENDIENTE_CUENTA;
        liquidacion.simulada = simulada;
        liquidacion.generadaEn = corte;
        liquidacion.generadaPor = adminId;
        return liquidacion;
    }

    public boolean esPorPagar() {
        return EstadoLiquidacion.POR_PAGAR.contains(estado);
    }

    /** Vacío = pagable. Orden: {@code YA_PAGADA}, {@code SIN_CUENTA}, {@code CUENTA_RETENIDA} (§ 8, D5, D7). */
    public Optional<MotivoNoPagable> motivoNoPagable(Optional<CuentaBancaria> cuenta, Instant ahora) {
        if (estado == EstadoLiquidacion.PAGADA) {
            return Optional.of(MotivoNoPagable.YA_PAGADA);
        }
        if (cuenta.isEmpty()) {
            return Optional.of(MotivoNoPagable.SIN_CUENTA);
        }
        if (cuenta.get().estaRetenida(ahora)) {
            return Optional.of(MotivoNoPagable.CUENTA_RETENIDA);
        }
        return Optional.empty();
    }

    /**
     * Deja la liquidación {@code PAGADA} con el destino enmascarado (S7, DT-11): el número completo nunca se copia.
     *
     * @throws IllegalStateException si no es pagable con esa cuenta en ese instante.
     */
    public void marcarPagada(String numeroOperacion, CuentaBancaria cuenta, Long adminId, Instant ahora) {
        var motivo = motivoNoPagable(Optional.ofNullable(cuenta), ahora);
        if (motivo.isPresent()) {
            throw new IllegalStateException("La liquidación " + id + " no se puede pagar: " + motivo.get());
        }
        this.estado = EstadoLiquidacion.PAGADA;
        this.numeroOperacion = numeroOperacion;
        this.pagadaEn = ahora;
        this.pagadaPor = adminId;
        this.bancoDestino = cuenta.getBanco();
        this.cuentaDestino = cuenta.enmascarado();
    }

    public Long getId() {
        return id;
    }

    public Long getArrendadorId() {
        return arrendadorId;
    }

    public LocalDate getPeriodo() {
        return periodo;
    }

    public BigDecimal getMontoBruto() {
        return montoBruto;
    }

    public BigDecimal getComision() {
        return comision;
    }

    public BigDecimal getMontoNeto() {
        return montoNeto;
    }

    public EstadoLiquidacion getEstado() {
        return estado;
    }

    public boolean isSimulada() {
        return simulada;
    }

    public Instant getGeneradaEn() {
        return generadaEn;
    }

    public Long getGeneradaPor() {
        return generadaPor;
    }

    public String getNumeroOperacion() {
        return numeroOperacion;
    }

    public Instant getPagadaEn() {
        return pagadaEn;
    }

    public Long getPagadaPor() {
        return pagadaPor;
    }

    public String getBancoDestino() {
        return bancoDestino;
    }

    public String getCuentaDestino() {
        return cuentaDestino;
    }
}
