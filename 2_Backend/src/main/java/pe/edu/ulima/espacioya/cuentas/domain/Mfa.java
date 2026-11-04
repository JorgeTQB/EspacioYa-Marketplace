package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import pe.edu.ulima.espacioya.comun.cifrado.ConvertidorCifrado;

/**
 * Configuración MFA 1-1 de una cuenta: la PK es el id del usuario. {@code activo = false} es una configuración
 * pendiente (secreto generado, aún sin confirmar con un código). Implementa {@link Persistable} porque el id viene
 * asignado, igual que {@link PerfilArrendador}.
 */
@Entity
@Table(name = "mfa")
public class Mfa implements Persistable<Long> {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Convert(converter = ConvertidorCifrado.class)
    @Column(nullable = false, columnDefinition = "text")
    private String secreto;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "activado_en")
    private Instant activadoEn;

    @Column(name = "ultimo_paso")
    private Long ultimoPaso;

    @Transient
    private boolean nuevo = true;

    protected Mfa() {
    }

    public static Mfa pendiente(Long usuarioId, String secretoBase32, Instant ahora) {
        var mfa = new Mfa();
        mfa.usuarioId = usuarioId;
        mfa.secreto = secretoBase32;
        mfa.activo = false;
        mfa.creadoEn = ahora;
        mfa.activadoEn = null;
        mfa.ultimoPaso = null;
        return mfa;
    }

    public void reemplazarSecreto(String secretoBase32, Instant ahora) {
        if (activo) {
            throw new IllegalStateException("No se reemplaza el secreto de un MFA activo");
        }
        secreto = secretoBase32;
        creadoEn = ahora;
        ultimoPaso = null;
    }

    public void activar(Instant ahora) {
        if (activo) {
            throw new IllegalStateException("El MFA ya está activo");
        }
        activo = true;
        activadoEn = ahora;
    }

    public void registrarPaso(long paso) {
        if (ultimoPaso != null && paso <= ultimoPaso) {
            throw new IllegalArgumentException("El paso TOTP ya se usó");
        }
        ultimoPaso = paso;
    }

    public boolean estaActivo() {
        return activo;
    }

    public byte[] secretoEnBytes() {
        return Base32.decodificar(secreto);
    }

    @PostLoad
    @PostPersist
    void marcarGuardado() {
        nuevo = false;
    }

    @Override
    public Long getId() {
        return usuarioId;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getSecreto() {
        return secreto;
    }

    public Long getUltimoPaso() {
        return ultimoPaso;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActivadoEn() {
        return activadoEn;
    }
}
