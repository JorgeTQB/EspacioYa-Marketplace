package pe.edu.ulima.espacioya.espacios.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fecha completa en la que el espacio no se puede reservar (HU-08). */
@Entity
@Table(name = "bloqueo_fecha")
public class BloqueoFecha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "espacio_id", nullable = false)
    private Long espacioId;

    @Column(nullable = false)
    private LocalDate fecha;

    protected BloqueoFecha() {
    }

    public static BloqueoFecha de(Long espacioId, LocalDate fecha) {
        var bloqueo = new BloqueoFecha();
        bloqueo.espacioId = espacioId;
        bloqueo.fecha = fecha;
        return bloqueo;
    }

    public Long getId() {
        return id;
    }

    public Long getEspacioId() {
        return espacioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
