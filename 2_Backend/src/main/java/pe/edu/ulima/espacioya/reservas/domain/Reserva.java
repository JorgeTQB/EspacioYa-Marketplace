package pe.edu.ulima.espacioya.reservas.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reserva de bloques de un espacio (HU-12). Nace en {@code PENDIENTE_PAGO} y retiene sus bloques 15 minutos. Una
 * pendiente vencida deja de ocupar al instante, aunque su fila siga en {@code PENDIENTE_PAGO} hasta que alguien la
 * expire: por eso quien lee usa {@link #estadoEn} y no {@link #getEstado} (HU-12 D2).
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    public static final int MINUTOS_RETENCION_RESERVA = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "espacio_id", nullable = false)
    private Long espacioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(name = "precio_hora_aplicado", nullable = false, precision = 8, scale = 2)
    private BigDecimal precioHoraAplicado;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelada_por", length = 20)
    private QuienCancela canceladaPor;

    @Column(name = "cancelada_en")
    private Instant canceladaEn;

    @Column(name = "monto_reembolso", precision = 10, scale = 2)
    private BigDecimal montoReembolso;

    // HU-25 D3: marca de idempotencia del recordatorio 24 h antes ("como mucho una vez").
    @Column(name = "recordatorio_enviado_en")
    private Instant recordatorioEnviadoEn;

    protected Reserva() {
    }

    public static Reserva pendiente(Long usuarioId, Long espacioId, BigDecimal precioHora, int bloques, Instant ahora) {
        var reserva = new Reserva();
        reserva.usuarioId = usuarioId;
        reserva.espacioId = espacioId;
        reserva.estado = EstadoReserva.PENDIENTE_PAGO;
        reserva.precioHoraAplicado = precioHora;
        reserva.montoTotal = precioHora.multiply(BigDecimal.valueOf(bloques));
        reserva.creadaEn = ahora;
        reserva.expiraEn = ahora.plus(Duration.ofMinutes(MINUTOS_RETENCION_RESERVA));
        return reserva;
    }

    public boolean esVigente(Instant ahora) {
        return estado == EstadoReserva.PENDIENTE_PAGO && ahora.isBefore(expiraEn);
    }

    public boolean estaVencida(Instant ahora) {
        return estado == EstadoReserva.PENDIENTE_PAGO && !ahora.isBefore(expiraEn);
    }

    public EstadoReserva estadoEn(Instant ahora) {
        return estaVencida(ahora) ? EstadoReserva.EXPIRADA : estado;
    }

    public long segundosRestantes(Instant ahora) {
        return esVigente(ahora) ? Duration.between(ahora, expiraEn).getSeconds() : 0;
    }

    public void expirar(Instant ahora) {
        if (!estaVencida(ahora)) {
            throw new IllegalStateException("Solo se expira una reserva vencida; estado " + estado);
        }
        estado = EstadoReserva.EXPIRADA;
    }

    /** Sus bloques siguen {@code activo = true}: desde ahora el puerto los informa como pagados (HU-13 R32). */
    public void confirmar(Instant ahora) {
        if (!esVigente(ahora)) {
            throw new IllegalStateException("Solo se confirma una reserva vigente; estado " + estado);
        }
        estado = EstadoReserva.CONFIRMADA;
    }

    /**
     * Solo se cancela una {@code CONFIRMADA} que aún no empieza (HU-15 D2). Orden: {@code YA_CANCELADA},
     * {@code NO_CONFIRMADA} (estado leído {@code PENDIENTE_PAGO} o {@code EXPIRADA}) y {@code YA_INICIADA}.
     */
    public Optional<MotivoNoCancelable> motivoNoCancelable(Instant inicio, Instant ahora) {
        var leido = estadoEn(ahora);
        if (leido == EstadoReserva.CANCELADA) {
            return Optional.of(MotivoNoCancelable.YA_CANCELADA);
        }
        if (leido != EstadoReserva.CONFIRMADA) {
            return Optional.of(MotivoNoCancelable.NO_CONFIRMADA);
        }
        if (!ahora.isBefore(inicio)) {
            return Optional.of(MotivoNoCancelable.YA_INICIADA);
        }
        return Optional.empty();
    }

    /** Deja la reserva {@code CANCELADA} con quién, cuándo y el monto a reembolsar (HU-15 R19, R20). */
    public void cancelar(QuienCancela quien, Instant inicio, BigDecimal montoReembolso, Instant ahora) {
        var motivo = motivoNoCancelable(inicio, ahora);
        if (motivo.isPresent()) {
            throw new IllegalStateException("La reserva no se puede cancelar: " + motivo.get());
        }
        estado = EstadoReserva.CANCELADA;
        canceladaPor = quien;
        canceladaEn = ahora;
        this.montoReembolso = montoReembolso;
    }

    /**
     * Si su estado admite liquidación al arrendador (HU-19 D3): {@code CONFIRMADA}, o {@code CANCELADA} por el
     * {@code USUARIO} con {@code monto_reembolso = 0} (RN-06, menos de 48 h). Lee la fila: una {@code CONFIRMADA} no
     * cambia sola y una pendiente nunca se liquida. Las 48 h tras el fin las decide {@code pagos}.
     */
    public boolean admiteLiquidacion() {
        if (estado == EstadoReserva.CONFIRMADA) {
            return true;
        }
        return estado == EstadoReserva.CANCELADA && canceladaPor == QuienCancela.USUARIO
                && montoReembolso != null && montoReembolso.signum() == 0;
    }

    /** {@code CONFIRMADA} y aún sin recordatorio (HU-25 R14, R17, R18). El plazo lo da {@link ReglaRecordatorio}. */
    public boolean admiteRecordatorio() {
        return estado == EstadoReserva.CONFIRMADA && recordatorioEnviadoEn == null;
    }

    public void marcarRecordatorio(Instant ahora) {
        if (!admiteRecordatorio()) {
            throw new IllegalStateException("La reserva no admite recordatorio: " + estado);
        }
        recordatorioEnviadoEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getEspacioId() {
        return espacioId;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public BigDecimal getPrecioHoraAplicado() {
        return precioHoraAplicado;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public QuienCancela getCanceladaPor() {
        return canceladaPor;
    }

    public Instant getCanceladaEn() {
        return canceladaEn;
    }

    public BigDecimal getMontoReembolso() {
        return montoReembolso;
    }

    public Instant getRecordatorioEnviadoEn() {
        return recordatorioEnviadoEn;
    }
}
