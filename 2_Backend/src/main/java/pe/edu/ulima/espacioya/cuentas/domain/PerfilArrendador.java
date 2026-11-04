package pe.edu.ulima.espacioya.cuentas.domain;

import java.util.EnumSet;
import java.util.Objects;

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
    public static final String AVISO_IDENTIDAD_EN_REVISION =
            "Estamos revisando tus documentos de identidad. Te avisaremos cuando terminemos.";
    public static final String AVISO_IDENTIDAD_RECHAZADA =
            "Tu identidad fue rechazada. Vuelve a subir tus documentos. Motivo: ";

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

    // RN-01: se envían documentos al empezar o tras un rechazo; en revisión o verificada, no.
    public boolean puedeEnviarDocumentos() {
        return estadoIdentidad == EstadoIdentidad.NO_INICIADA || estadoIdentidad == EstadoIdentidad.RECHAZADA;
    }

    public void enviarDocumentos() {
        if (!puedeEnviarDocumentos()) {
            throw new IllegalStateException("La identidad no admite un nuevo envío en estado " + estadoIdentidad);
        }
        estadoIdentidad = EstadoIdentidad.EN_REVISION;
        motivoRechazo = null;
    }

    // RN-01: solo el admin decide, y solo sobre una identidad en revisión (HU-06).
    public void aprobarIdentidad() {
        exigirEnRevision();
        estadoIdentidad = EstadoIdentidad.VERIFICADA;
        motivoRechazo = null;
    }

    public void rechazarIdentidad(String motivo) {
        exigirEnRevision();
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalStateException("El rechazo de la identidad exige un motivo");
        }
        estadoIdentidad = EstadoIdentidad.RECHAZADA;
        motivoRechazo = motivo.strip();
    }

    private void exigirEnRevision() {
        if (estadoIdentidad != EstadoIdentidad.EN_REVISION) {
            throw new IllegalStateException("La identidad no está en revisión: " + estadoIdentidad);
        }
    }

    // HU-20 D10: en revisión, el admin podría aprobar un documento o una razón social que no vio.
    public boolean admiteCambioDeIdentidad() {
        return estadoIdentidad != EstadoIdentidad.EN_REVISION;
    }

    /** Lo que cambiaría {@link #actualizarFacturacion} sin aplicarlo; un número {@code null} conserva el actual. */
    public CambioFacturacion cambiosPropuestos(TipoDocumento tipo, String numeroNormalizado, String razonSocialNueva,
                                               String direccionFiscalNueva) {
        var campos = EnumSet.noneOf(CampoFacturacion.class);
        if (tipo != tipoDocumento) {
            campos.add(CampoFacturacion.TIPO_DOCUMENTO);
        }
        if (numeroNormalizado != null && !numeroNormalizado.equals(numeroDocumento)) {
            campos.add(CampoFacturacion.NUMERO_DOCUMENTO);
        }
        if (!Objects.equals(recortar(razonSocialNueva), razonSocial)) {
            campos.add(CampoFacturacion.RAZON_SOCIAL);
        }
        if (!Objects.equals(recortar(direccionFiscalNueva), direccionFiscal)) {
            campos.add(CampoFacturacion.DIRECCION_FISCAL);
        }
        var propuesto = new CambioFacturacion(campos, estadoIdentidad, estadoIdentidad);
        // RN-02 (y D9): una identidad verificada con otro documento u otra razón social vuelve a revisión.
        var nueva = estadoIdentidad == EstadoIdentidad.VERIFICADA && propuesto.cambiaIdentidad()
                ? EstadoIdentidad.EN_REVISION
                : estadoIdentidad;
        return new CambioFacturacion(campos, estadoIdentidad, nueva);
    }

    /**
     * Aplica los cambios: con la identidad {@code VERIFICADA}, un documento o una razón social distintos la dejan
     * {@code EN_REVISION} (HU-20 R37, R38). {@code numeroNormalizado}/{@code hashDocumento} {@code null} conservan el
     * documento actual. {@code NO_INICIADA} y {@code RECHAZADA} conservan su estado y su motivo (R40).
     *
     * @throws IllegalStateException si la identidad está en revisión y cambia el documento o la razón social (R41).
     * @throws IllegalArgumentException si cambia el tipo sin un número nuevo, o el número sin su huella.
     */
    public CambioFacturacion actualizarFacturacion(TipoDocumento tipo, String numeroNormalizado, String hashNuevo,
                                                   String razonSocialNueva, String direccionFiscalNueva) {
        var cambio = cambiosPropuestos(tipo, numeroNormalizado, razonSocialNueva, direccionFiscalNueva);
        if (cambio.cambiaIdentidad() && !admiteCambioDeIdentidad()) {
            throw new IllegalStateException("La identidad está en revisión: no admite otro documento ni razón social");
        }
        var campos = cambio.campos();
        if (campos.contains(CampoFacturacion.TIPO_DOCUMENTO) && !campos.contains(CampoFacturacion.NUMERO_DOCUMENTO)) {
            throw new IllegalArgumentException("Cambiar el tipo de documento exige un número nuevo");
        }
        if (campos.contains(CampoFacturacion.NUMERO_DOCUMENTO)) {
            if (hashNuevo == null) {
                throw new IllegalArgumentException("Un número de documento nuevo exige su huella");
            }
            numeroDocumento = numeroNormalizado;
            hashDocumento = hashNuevo;
        }
        tipoDocumento = tipo;
        razonSocial = recortar(razonSocialNueva);
        direccionFiscal = recortar(direccionFiscalNueva);
        estadoIdentidad = cambio.identidadNueva();
        return cambio;
    }

    public String numeroEnmascarado() {
        if (numeroDocumento == null || numeroDocumento.length() <= 4) {
            return "****";
        }
        return "****" + numeroDocumento.substring(numeroDocumento.length() - 4);
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }

    public String avisoIdentidad() {
        return switch (estadoIdentidad) {
            case NO_INICIADA -> AVISO_IDENTIDAD;
            case EN_REVISION -> AVISO_IDENTIDAD_EN_REVISION;
            case RECHAZADA -> AVISO_IDENTIDAD_RECHAZADA + (motivoRechazo == null ? "" : motivoRechazo);
            case VERIFICADA -> null;
        };
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
