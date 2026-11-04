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

/**
 * Paso intermedio del inicio de sesión con MFA (HU-17 DT-1): no es una sesión, solo se canjea por una en
 * {@code POST /api/sesiones/mfa}. Se guarda el SHA-256 del token, nunca el token.
 */
@Entity
@Table(name = "desafio_mfa")
public class DesafioMfa {

    public static final Duration VIGENCIA = Duration.ofMinutes(5);
    public static final int MAXIMO_FALLOS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_token", nullable = false, length = 64)
    private String hashToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasoMfa paso;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "cerrado_en")
    private Instant cerradoEn;

    protected DesafioMfa() {
    }

    public static DesafioMfa crear(Long usuarioId, String hashToken, PasoMfa paso, Instant ahora) {
        var desafio = new DesafioMfa();
        desafio.usuarioId = usuarioId;
        desafio.hashToken = hashToken;
        desafio.paso = paso;
        desafio.creadoEn = ahora;
        desafio.expiraEn = ahora.plus(VIGENCIA);
        desafio.intentosFallidos = 0;
        desafio.cerradoEn = null;
        return desafio;
    }

    public boolean estaAbierto(Instant ahora) {
        return cerradoEn == null && ahora.isBefore(expiraEn);
    }

    // Al llegar al máximo se cierra: hay que volver a poner la contraseña (HU-17 P2).
    public boolean registrarFallo(Instant ahora) {
        if (cerradoEn != null) {
            throw new IllegalStateException("El desafío ya está cerrado");
        }
        intentosFallidos++;
        if (intentosFallidos >= MAXIMO_FALLOS) {
            cerradoEn = ahora;
            return true;
        }
        return false;
    }

    public void cerrar(Instant ahora) {
        if (cerradoEn != null) {
            throw new IllegalStateException("El desafío ya está cerrado");
        }
        cerradoEn = ahora;
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

    public PasoMfa getPaso() {
        return paso;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getCerradoEn() {
        return cerradoEn;
    }
}
