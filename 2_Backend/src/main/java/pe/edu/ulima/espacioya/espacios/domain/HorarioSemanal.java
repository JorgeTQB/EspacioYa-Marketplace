package pe.edu.ulima.espacioya.espacios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una franja del horario semanal de un espacio (HU-08). El horario se reemplaza completo, nunca fila a fila. */
@Entity
@Table(name = "horario_semanal")
public class HorarioSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "espacio_id", nullable = false)
    private Long espacioId;

    @Column(name = "dia_semana", nullable = false)
    private short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private short horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private short horaFin;

    protected HorarioSemanal() {
    }

    public static HorarioSemanal de(Long espacioId, FranjaHoraria franja) {
        var fila = new HorarioSemanal();
        fila.espacioId = espacioId;
        fila.diaSemana = (short) franja.diaSemana();
        fila.horaInicio = (short) franja.horaInicio();
        fila.horaFin = (short) franja.horaFin();
        return fila;
    }

    public FranjaHoraria aFranja() {
        return new FranjaHoraria(diaSemana, horaInicio, horaFin);
    }

    public Long getId() {
        return id;
    }

    public Long getEspacioId() {
        return espacioId;
    }
}
