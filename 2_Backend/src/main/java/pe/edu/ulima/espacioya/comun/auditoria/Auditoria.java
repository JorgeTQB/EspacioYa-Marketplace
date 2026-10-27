package pe.edu.ulima.espacioya.comun.auditoria;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo el id: comun no depende de cuentas. La FK a usuario existe en la BD.
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 60)
    private String accion;

    @Column(length = 60)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false)
    private Instant fecha;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    protected Auditoria() {
    }

    public static Auditoria de(EventoAuditoria evento, Instant fecha) {
        var auditoria = new Auditoria();
        auditoria.usuarioId = evento.usuarioId();
        auditoria.accion = evento.accion();
        auditoria.entidad = evento.entidad();
        auditoria.entidadId = evento.entidadId();
        auditoria.ip = evento.ip();
        auditoria.fecha = fecha;
        auditoria.detalle = evento.detalle();
        return auditoria;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public String getIp() {
        return ip;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getDetalle() {
        return detalle;
    }
}
