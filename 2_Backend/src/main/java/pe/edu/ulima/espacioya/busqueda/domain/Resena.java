package pe.edu.ulima.espacioya.busqueda.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reseña de una reserva usada (HU-24). Una por reserva: {@code uq_resena_reserva}, en cualquier estado (P12). */
@Entity
@Table(name = "resena")
public class Resena {

    /** Reemplaza al texto libre al eliminar la cuenta del autor (HU-22, P11). */
    public static final String COMENTARIO_ELIMINADO = "Comentario eliminado.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "espacio_id", nullable = false)
    private Long espacioId;

    // short y no int: ddl-auto=validate exige el tipo exacto de la columna SMALLINT (DT-12).
    @Column(nullable = false)
    private short calificacion;

    @Column(nullable = false, length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoResena estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Resena() {
    }

    public static Resena nueva(Long reservaId, Long usuarioId, Long espacioId, int calificacion, String comentario,
                               Instant ahora) {
        var resena = new Resena();
        resena.reservaId = reservaId;
        resena.usuarioId = usuarioId;
        resena.espacioId = espacioId;
        resena.calificacion = (short) calificacion;
        resena.comentario = comentario;
        resena.estado = EstadoResena.VISIBLE;
        resena.creadoEn = ahora;
        return resena;
    }

    public boolean estaVisible() {
        return estado == EstadoResena.VISIBLE;
    }

    /** HU-27: la oculta el admin por moderación. {@code OCULTA} es final. */
    public void ocultar() {
        if (!estaVisible()) {
            throw new IllegalStateException("Solo se oculta una reseña visible, no en " + estado);
        }
        estado = EstadoResena.OCULTA;
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

    public Long getEspacioId() {
        return espacioId;
    }

    public int getCalificacion() {
        return calificacion;
    }

    public String getComentario() {
        return comentario;
    }

    public EstadoResena getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
