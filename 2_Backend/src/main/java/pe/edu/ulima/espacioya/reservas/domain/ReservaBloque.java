package pe.edu.ulima.espacioya.reservas.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una hora retenida por una reserva (HU-12). Liberar un bloque pone {@code activo = false}: la fila se conserva
 * porque HU-14 muestra el horario de las reservas pasadas.
 */
@Entity
@Table(name = "reserva_bloque")
public class ReservaBloque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "espacio_id", nullable = false)
    private Long espacioId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private short horaInicio;

    @Column(nullable = false)
    private boolean activo;

    protected ReservaBloque() {
    }

    public ReservaBloque(Long reservaId, Long espacioId, LocalDate fecha, int horaInicio) {
        this.reservaId = reservaId;
        this.espacioId = espacioId;
        this.fecha = fecha;
        this.horaInicio = (short) horaInicio;
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public Long getEspacioId() {
        return espacioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public int getHoraInicio() {
        return horaInicio;
    }

    public boolean isActivo() {
        return activo;
    }
}
