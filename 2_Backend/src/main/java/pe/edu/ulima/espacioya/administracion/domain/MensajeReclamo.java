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

/** Mensaje del hilo de un reclamo, de una de sus dos partes (HU-26 P1). No se edita ni se borra (R30). */
@Entity
@Table(name = "mensaje_reclamo")
public class MensajeReclamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reclamo_id", nullable = false)
    private Long reclamoId;

    @Column(name = "autor_id", nullable = false)
    private Long autorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PapelReclamo papel;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(nullable = false)
    private Instant fecha;

    protected MensajeReclamo() {
    }

    public static MensajeReclamo nuevo(Long reclamoId, Long autorId, PapelReclamo papel, String texto, Instant ahora) {
        var mensaje = new MensajeReclamo();
        mensaje.reclamoId = reclamoId;
        mensaje.autorId = autorId;
        mensaje.papel = papel;
        mensaje.texto = texto;
        mensaje.fecha = ahora;
        return mensaje;
    }

    public Long getId() {
        return id;
    }

    public Long getReclamoId() {
        return reclamoId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public PapelReclamo getPapel() {
        return papel;
    }

    public String getTexto() {
        return texto;
    }

    public Instant getFecha() {
        return fecha;
    }
}
