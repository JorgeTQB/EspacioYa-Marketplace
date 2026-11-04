package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "token")
public class Token {

    public static final Duration VIGENCIA_RECUPERACION = Duration.ofMinutes(30);
    public static final Duration VIGENCIA_CAMBIO_CORREO = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoToken tipo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_token", nullable = false, length = 64)
    private String hashToken;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    protected Token() {
    }

    public static Token recuperacion(Long usuarioId, String hashToken, Instant ahora) {
        return nuevo(usuarioId, TipoToken.RECUPERACION, hashToken, ahora, VIGENCIA_RECUPERACION);
    }

    // El token no guarda el correo destino: vive en usuario.correo_pendiente, uno a la vez (HU-20 D1).
    public static Token cambioCorreo(Long usuarioId, String hashToken, Instant ahora) {
        return nuevo(usuarioId, TipoToken.CAMBIO_CORREO, hashToken, ahora, VIGENCIA_CAMBIO_CORREO);
    }

    private static Token nuevo(Long usuarioId, TipoToken tipo, String hashToken, Instant ahora, Duration vigencia) {
        var token = new Token();
        token.usuarioId = usuarioId;
        token.tipo = tipo;
        token.hashToken = hashToken;
        token.creadoEn = ahora;
        token.expiraEn = ahora.plus(vigencia);
        return token;
    }

    public boolean fueUsado() {
        return usadoEn != null;
    }

    public boolean expiro(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public TipoToken getTipo() {
        return tipo;
    }

    public String getHashToken() {
        return hashToken;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }
}
