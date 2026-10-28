package pe.edu.ulima.espacioya.cuentas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import pe.edu.ulima.espacioya.comun.cifrado.ConvertidorCifrado;

/**
 * Perfil 1-1 del usuario arrendador: la PK es el id del usuario. Implementa {@link Persistable}
 * porque el id viene asignado y así {@code save} hace {@code persist} y no un {@code merge}.
 */
@Entity
@Table(name = "perfil_arrendador")
public class PerfilArrendador implements Persistable<Long> {

    public static final String AVISO_IDENTIDAD = "Para publicar tus espacios, primero debes verificar tu identidad.";

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 3)
    private TipoDocumento tipoDocumento;

    @Convert(converter = ConvertidorCifrado.class)
    @Column(name = "numero_documento", nullable = false, columnDefinition = "text")
    private String numeroDocumento;

    @Column(name = "hash_documento", nullable = false, length = 64)
    private String hashDocumento;

    @Convert(converter = ConvertidorCifrado.class)
    @Column(name = "razon_social", nullable = false, columnDefinition = "text")
    private String razonSocial;

    @Convert(converter = ConvertidorCifrado.class)
    @Column(name = "direccion_fiscal", nullable = false, columnDefinition = "text")
    private String direccionFiscal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_identidad", nullable = false, length = 20)
    private EstadoIdentidad estadoIdentidad;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Transient
    private boolean nuevo = true;

    protected PerfilArrendador() {
    }

    public static PerfilArrendador crear(Long usuarioId, TipoDocumento tipo, String numeroNormalizado,
                                         String hashDocumento, String razonSocial, String direccionFiscal) {
        var perfil = new PerfilArrendador();
        perfil.usuarioId = usuarioId;
        perfil.tipoDocumento = tipo;
        perfil.numeroDocumento = numeroNormalizado;
        perfil.hashDocumento = hashDocumento;
        perfil.razonSocial = razonSocial.trim();
        perfil.direccionFiscal = direccionFiscal.trim();
        perfil.estadoIdentidad = EstadoIdentidad.NO_INICIADA;
        perfil.motivoRechazo = null;
        return perfil;
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

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getHashDocumento() {
        return hashDocumento;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getDireccionFiscal() {
        return direccionFiscal;
    }

    public EstadoIdentidad getEstadoIdentidad() {
        return estadoIdentidad;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }
}
