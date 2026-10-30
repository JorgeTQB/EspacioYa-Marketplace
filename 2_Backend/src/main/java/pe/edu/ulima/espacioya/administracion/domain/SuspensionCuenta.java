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
 * Una suspensión o reactivación de una cuenta hecha por un admin (HU-09, STRIDE R). Es historial: no se modifica. El
 * estado vigente vive en {@code usuario.estado}, dentro de {@code cuentas}; aquí solo están los ids (las FK existen
 * en la BD), porque {@code administracion} no usa las entidades de otro módulo.
 */
@Entity
@Table(name = "suspension_cuenta")
public class SuspensionCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionSuspension accion;

    @Column(length = 500)
    private String motivo;

    @Column(nullable = false)
    private Instant fecha;

    protected SuspensionCuenta() {
    }

    public static SuspensionCuenta suspender(Long usuarioId, Long adminId, String motivo, Instant fecha) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Una suspensión exige un motivo");
        }
        return nueva(usuarioId, adminId, AccionSuspension.SUSPENSION, motivo.strip(), fecha);
    }

    public static SuspensionCuenta reactivar(Long usuarioId, Long adminId, Instant fecha) {
        return nueva(usuarioId, adminId, AccionSuspension.REACTIVACION, null, fecha);
    }

    private static SuspensionCuenta nueva(Long usuarioId, Long adminId, AccionSuspension accion, String motivo,
                                          Instant fecha) {
        var registro = new SuspensionCuenta();
        registro.usuarioId = usuarioId;
        registro.adminId = adminId;
        registro.accion = accion;
        registro.motivo = motivo;
        registro.fecha = fecha;
        return registro;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public AccionSuspension getAccion() {
        return accion;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getFecha() {
        return fecha;
    }
}
