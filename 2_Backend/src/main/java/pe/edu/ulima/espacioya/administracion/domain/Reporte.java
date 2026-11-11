package pe.edu.ulima.espacioya.administracion.domain;

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
 * Reporte de un usuario sobre un espacio o una reseña (HU-27). Guarda {@code espacio_id} o {@code resena_id}, nunca
 * los dos (DT-4, {@code ck_reporte_objetivo}). La decisión del admin queda en el estado y en {@code por_fraude}; la
 * coherencia con el estado la repiten {@code ck_reporte_decision} y {@code ck_reporte_fraude} (R57, R75).
 */
@Entity
@Table(name = "reporte")
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reportante_id", nullable = false)
    private Long reportanteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_objetivo", nullable = false, length = 10)
    private TipoObjetivoReporte tipoObjetivo;

    @Column(name = "espacio_id")
    private Long espacioId;

    @Column(name = "resena_id")
    private Long resenaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoReporte motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoReporte estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "motivo_decision", length = 500)
    private String motivoDecision;

    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    @Column(name = "por_fraude", nullable = false)
    private boolean porFraude;

    protected Reporte() {
    }

    public static Reporte deEspacio(Long reportanteId, Long espacioId, MotivoReporte motivo, Instant ahora) {
        var reporte = nuevo(reportanteId, TipoObjetivoReporte.ESPACIO, motivo, ahora);
        reporte.espacioId = espacioId;
        return reporte;
    }

    public static Reporte deResena(Long reportanteId, Long resenaId, MotivoReporte motivo, Instant ahora) {
        var reporte = nuevo(reportanteId, TipoObjetivoReporte.RESENA, motivo, ahora);
        reporte.resenaId = resenaId;
        return reporte;
    }

    private static Reporte nuevo(Long reportanteId, TipoObjetivoReporte tipo, MotivoReporte motivo, Instant ahora) {
        var reporte = new Reporte();
        reporte.reportanteId = reportanteId;
        reporte.tipoObjetivo = tipo;
        reporte.motivo = motivo;
        reporte.estado = EstadoReporte.POR_REVISAR;
        reporte.creadoEn = ahora;
        return reporte;
    }

    /** El id del espacio o de la reseña, según el tipo. */
    public Long getObjetivoId() {
        return esDeEspacio() ? espacioId : resenaId;
    }

    public boolean esDeEspacio() {
        return tipoObjetivo == TipoObjetivoReporte.ESPACIO;
    }

    public boolean estaPorRevisar() {
        return estado == EstadoReporte.POR_REVISAR;
    }

    /** Vacío mientras está POR_REVISAR; {@code OCULTAR_POR_FRAUDE} si se ocultó por fraude (R78). */
    public Optional<DecisionReporte> decision() {
        return switch (estado) {
            case POR_REVISAR -> Optional.empty();
            case MANTENIDO -> Optional.of(DecisionReporte.MANTENER);
            case OCULTADO -> Optional.of(porFraude ? DecisionReporte.OCULTAR_POR_FRAUDE : DecisionReporte.OCULTAR);
        };
    }

    /**
     * MANTENER → MANTENIDO; OCULTAR → OCULTADO; OCULTAR_POR_FRAUDE → OCULTADO con {@code porFraude}. Un reporte ya
     * resuelto o el fraude sobre una reseña son errores de programación: el servicio los rechaza antes.
     */
    public void resolver(DecisionReporte decision, Long adminId, String motivoDecision, Instant ahora) {
        if (!estaPorRevisar()) {
            throw new IllegalStateException("El reporte " + id + " ya se resolvió");
        }
        if (decision.esFraude() && !esDeEspacio()) {
            throw new IllegalStateException("Solo un reporte de espacio se oculta por fraude");
        }
        this.estado = decision.oculta() ? EstadoReporte.OCULTADO : EstadoReporte.MANTENIDO;
        this.porFraude = decision.esFraude();
        this.adminId = adminId;
        this.motivoDecision = motivoDecision;
        this.resueltoEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public Long getReportanteId() {
        return reportanteId;
    }

    public TipoObjetivoReporte getTipoObjetivo() {
        return tipoObjetivo;
    }

    public Long getEspacioId() {
        return espacioId;
    }

    public Long getResenaId() {
        return resenaId;
    }

    public MotivoReporte getMotivo() {
        return motivo;
    }

    public EstadoReporte getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getMotivoDecision() {
        return motivoDecision;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public boolean isPorFraude() {
        return porFraude;
    }
}
