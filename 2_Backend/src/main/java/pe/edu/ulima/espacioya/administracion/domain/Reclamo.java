package pe.edu.ulima.espacioya.administracion.domain;

import java.math.BigDecimal;
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
 * Reclamo sobre una reserva (HU-26). Uno por reserva ({@code uq_reclamo_reserva}). Guarda sus dos partes, copiadas al
 * abrirlo, para resolver la propiedad sin cruzar módulos (DT-14). La coherencia de la resolución con el estado la
 * repite {@code ck_reclamo_resolucion} (R65).
 */
@Entity
@Table(name = "reclamo")
public class Reclamo {

    /** Reemplaza la descripción y los mensajes del titular al eliminar su cuenta (HU-22, P10). */
    public static final String TEXTO_ELIMINADO = "Texto eliminado.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "arrendador_id", nullable = false)
    private Long arrendadorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoReclamo motivo;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReclamo estado;

    @Column(name = "abierto_en", nullable = false)
    private Instant abiertoEn;

    @Column(length = 500)
    private String resolucion;

    @Column(name = "resuelto_por")
    private Long resueltoPor;

    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    @Column(name = "monto_reembolso", precision = 10, scale = 2)
    private BigDecimal montoReembolso;

    protected Reclamo() {
    }

    public static Reclamo abrir(Long reservaId, Long usuarioId, Long arrendadorId, MotivoReclamo motivo,
                                String descripcion, Instant ahora) {
        var reclamo = new Reclamo();
        reclamo.reservaId = reservaId;
        reclamo.usuarioId = usuarioId;
        reclamo.arrendadorId = arrendadorId;
        reclamo.motivo = motivo;
        reclamo.descripcion = descripcion;
        reclamo.estado = EstadoReclamo.ABIERTO;
        reclamo.abiertoEn = ahora;
        return reclamo;
    }

    public boolean estaAbierto() {
        return estado == EstadoReclamo.ABIERTO;
    }

    /** USUARIO si lo abrió, ARRENDADOR si es del dueño del espacio, vacío para cualquier otra cuenta. */
    public Optional<PapelReclamo> papelDe(Long cuentaId) {
        if (usuarioId.equals(cuentaId)) {
            return Optional.of(PapelReclamo.USUARIO);
        }
        if (arrendadorId.equals(cuentaId)) {
            return Optional.of(PapelReclamo.ARRENDADOR);
        }
        return Optional.empty();
    }

    /** Vacío mientras está ABIERTO. */
    public Optional<PapelReclamo> aFavorDe() {
        return switch (estado) {
            case ABIERTO -> Optional.empty();
            case RESUELTO_USUARIO -> Optional.of(PapelReclamo.USUARIO);
            case RESUELTO_ARRENDADOR -> Optional.of(PapelReclamo.ARRENDADOR);
        };
    }

    public void resolverAFavorDelUsuario(Long adminId, String resolucion, BigDecimal monto, Instant ahora) {
        exigirAbierto();
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalStateException("El reembolso de un reclamo debe ser mayor que 0");
        }
        resolver(EstadoReclamo.RESUELTO_USUARIO, adminId, resolucion, ahora);
        this.montoReembolso = monto;
    }

    public void resolverAFavorDelArrendador(Long adminId, String resolucion, Instant ahora) {
        exigirAbierto();
        resolver(EstadoReclamo.RESUELTO_ARRENDADOR, adminId, resolucion, ahora);
    }

    private void resolver(EstadoReclamo nuevo, Long adminId, String texto, Instant ahora) {
        this.estado = nuevo;
        this.resueltoPor = adminId;
        this.resolucion = texto;
        this.resueltoEn = ahora;
    }

    private void exigirAbierto() {
        if (!estaAbierto()) {
            throw new IllegalStateException("El reclamo " + id + " ya se resolvió");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getArrendadorId() {
        return arrendadorId;
    }

    public MotivoReclamo getMotivo() {
        return motivo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public EstadoReclamo getEstado() {
        return estado;
    }

    public Instant getAbiertoEn() {
        return abiertoEn;
    }

    public String getResolucion() {
        return resolucion;
    }

    public Long getResueltoPor() {
        return resueltoPor;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public BigDecimal getMontoReembolso() {
        return montoReembolso;
    }
}
