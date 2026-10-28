package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sesion")
public class Sesion {

    public static final Duration INACTIVIDAD = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_token", nullable = false, length = 64)
    private String hashToken;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean revocada;

    protected Sesion() {
    }

    public static Sesion iniciar(Long usuarioId, String hashToken, Instant ahora) {
        var sesion = new Sesion();
        sesion.usuarioId = usuarioId;
        sesion.hashToken = hashToken;
        sesion.creadaEn = ahora;
        sesion.expiraEn = ahora.plus(INACTIVIDAD);
        sesion.revocada = false;
        return sesion;
    }

    public boolean estaVigente(Instant ahora) {
        return !revocada && ahora.isBefore(expiraEn);
    }

    public void renovar(Instant ahora) {
        expiraEn = ahora.plus(INACTIVIDAD);
    }

    public void revocar() {
        revocada = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getHashToken() {
        return hashToken;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public boolean isRevocada() {
        return revocada;
    }
}
