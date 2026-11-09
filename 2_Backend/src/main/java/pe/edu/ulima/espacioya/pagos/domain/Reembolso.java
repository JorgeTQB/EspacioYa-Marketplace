package pe.edu.ulima.espacioya.pagos.domain;

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
 * Reembolso contra el pago original (HU-16). Es la fuente de verdad del proceso: {@code pago.estado} solo cambia en la
 * misma transacción que este (D1). Un reembolso por pago y origen ({@code uq_reembolso_pago_origen}).
 */
@Entity
@Table(name = "reembolso")
public class Reembolso {

    /** Un PENDIENTE sin resultado durante este margen se considera sin respuesta y se puede reintentar (D5). */
    public static final Duration MARGEN_SIN_RESPUESTA = Duration.ofMinutes(2);

    private static final String PREFIJO_CLAVE = "espacioya-reembolso-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pago_id", nullable = false)
    private Long pagoId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenReembolso origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReembolso estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "ultimo_intento_en")
    private Instant ultimoIntentoEn;

    @Column(name = "id_reembolso_pasarela", length = 100)
    private String idReembolsoPasarela;

    protected Reembolso() {
    }

    public static Reembolso porCancelacion(Long pagoId, BigDecimal monto, Instant ahora) {
        return pendiente(pagoId, monto, OrigenReembolso.CANCELACION, ahora);
    }

    /** Reembolso total o parcial que decide el admin al resolver un reclamo a favor del usuario (HU-26 R44). */
    public static Reembolso porReclamo(Long pagoId, BigDecimal monto, Instant ahora) {
        return pendiente(pagoId, monto, OrigenReembolso.RECLAMO, ahora);
    }

    private static Reembolso pendiente(Long pagoId, BigDecimal monto, OrigenReembolso origen, Instant ahora) {
        var reembolso = new Reembolso();
        reembolso.pagoId = pagoId;
        reembolso.monto = monto;
        reembolso.origen = origen;
        reembolso.estado = EstadoReembolso.PENDIENTE;
        reembolso.intentos = 0;
        reembolso.creadoEn = ahora;
        return reembolso;
    }

    public Optional<MotivoNoReintentable> motivoNoReintentable(Instant ahora) {
        if (estado == EstadoReembolso.REEMBOLSADO) {
            return Optional.of(MotivoNoReintentable.YA_REEMBOLSADO);
        }
        if (estado == EstadoReembolso.PENDIENTE && desde().isAfter(ahora.minus(MARGEN_SIN_RESPUESTA))) {
            return Optional.of(MotivoNoReintentable.EN_PROCESO);
        }
        return Optional.empty();
    }

    /**
     * Si se puede empezar un intento ahora: el primero (PENDIENTE sin intentos, el automático tras la cancelación) o
     * uno reintentable. R22 prohíbe <em>otro</em> intento mientras uno está en proceso; el primero no tiene ninguno
     * antes, aunque el reembolso recién creado cuente como en proceso para el admin (R34).
     */
    public boolean admiteIntento(Instant ahora) {
        return esPrimerIntento() || motivoNoReintentable(ahora).isEmpty();
    }

    public void iniciarIntento(Instant ahora) {
        if (!admiteIntento(ahora)) {
            throw new IllegalStateException("El reembolso " + id + " no se puede intentar: "
                    + motivoNoReintentable(ahora).orElseThrow());
        }
        estado = EstadoReembolso.PENDIENTE;
        intentos++;
        ultimoIntentoEn = ahora;
    }

    public void marcarReembolsado(String idReembolsoPasarela) {
        exigirPendiente();
        estado = EstadoReembolso.REEMBOLSADO;
        this.idReembolsoPasarela = idReembolsoPasarela;
    }

    public void marcarFallido() {
        exigirPendiente();
        estado = EstadoReembolso.FALLIDO;
    }

    /** Igual en todos los intentos: la pasarela no reembolsa dos veces la misma clave (D6). */
    public String claveIdempotencia() {
        return PREFIJO_CLAVE + id;
    }

    private boolean esPrimerIntento() {
        return estado == EstadoReembolso.PENDIENTE && intentos == 0;
    }

    private Instant desde() {
        return ultimoIntentoEn != null ? ultimoIntentoEn : creadoEn;
    }

    private void exigirPendiente() {
        if (estado != EstadoReembolso.PENDIENTE) {
            throw new IllegalStateException("El reembolso " + id + " no está PENDIENTE");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPagoId() {
        return pagoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public OrigenReembolso getOrigen() {
        return origen;
    }

    public EstadoReembolso getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUltimoIntentoEn() {
        return ultimoIntentoEn;
    }

    public String getIdReembolsoPasarela() {
        return idReembolsoPasarela;
    }
}
