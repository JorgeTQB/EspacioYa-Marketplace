package pe.edu.ulima.espacioya.administracion.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una decisión del admin sobre la identidad de un arrendador (HU-06, STRIDE R). Es historial: no se modifica. El
 * estado vigente vive en el perfil del arrendador, dentro de {@code cuentas}; aquí solo están los ids (las FK
 * existen en la BD), porque {@code administracion} no usa las entidades de otro módulo.
 */
@Entity
@Table(name = "revision_identidad")
public class RevisionIdentidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arrendador_id", nullable = false)
    private Long arrendadorId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionIdentidad decision;

    @Column(length = 500)
    private String motivo;

    @Column(nullable = false)
    private Instant fecha;

    protected RevisionIdentidad() {
    }

    public static RevisionIdentidad aprobar(Long arrendadorId, Long adminId, Instant fecha) {
        return nueva(arrendadorId, adminId, DecisionIdentidad.APROBADA, null, fecha);
    }

    public static RevisionIdentidad rechazar(Long arrendadorId, Long adminId, String motivo, Instant fecha) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Un rechazo de identidad exige un motivo");
        }
        return nueva(arrendadorId, adminId, DecisionIdentidad.RECHAZADA, motivo.strip(), fecha);
    }

    private static RevisionIdentidad nueva(Long arrendadorId, Long adminId, DecisionIdentidad decision, String motivo,
                                           Instant fecha) {
        var revision = new RevisionIdentidad();
        revision.arrendadorId = arrendadorId;
        revision.adminId = adminId;
        revision.decision = decision;
        revision.motivo = motivo;
        revision.fecha = fecha;
        return revision;
    }

    public Long getId() {
        return id;
    }

    public Long getArrendadorId() {
        return arrendadorId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public DecisionIdentidad getDecision() {
        return decision;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getFecha() {
        return fecha;
    }
}
