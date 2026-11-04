package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Código de respaldo de un solo uso: solo se guarda el HMAC-SHA256 de su forma normalizada (HU-17 DT-7). */
@Entity
@Table(name = "codigo_respaldo")
public class CodigoRespaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_codigo", nullable = false, length = 64)
    private String hashCodigo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    protected CodigoRespaldo() {
    }

    public static CodigoRespaldo nuevo(Long usuarioId, String hashCodigo, Instant ahora) {
        var codigo = new CodigoRespaldo();
        codigo.usuarioId = usuarioId;
        codigo.hashCodigo = hashCodigo;
        codigo.creadoEn = ahora;
        codigo.usadoEn = null;
        return codigo;
    }

    public void usar(Instant ahora) {
        if (estaUsado()) {
            throw new IllegalStateException("El código de respaldo ya se usó");
        }
        usadoEn = ahora;
    }

    public boolean estaUsado() {
        return usadoEn != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getHashCodigo() {
        return hashCodigo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }
}
