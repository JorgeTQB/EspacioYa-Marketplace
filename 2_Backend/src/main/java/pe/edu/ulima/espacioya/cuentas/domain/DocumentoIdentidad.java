package pe.edu.ulima.espacioya.cuentas.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Documento de identidad vigente de un arrendador (como mucho uno por tipo). El contenido vive cifrado en
 * el almacenamiento privado: aquí solo está su clave aleatoria, nunca el nombre original del archivo.
 */
@Entity
@Table(name = "documento_identidad")
public class DocumentoIdentidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arrendador_id", nullable = false)
    private Long arrendadorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDocumentoIdentidad tipo;

    @Column(name = "ruta_archivo", nullable = false, length = 100)
    private String rutaArchivo;

    @Column(name = "tipo_mime", nullable = false, length = 30)
    private String tipoMime;

    @Column(nullable = false)
    private int tamano;

    @Column(name = "subido_en", nullable = false)
    private Instant subidoEn;

    protected DocumentoIdentidad() {
    }

    public static DocumentoIdentidad registrar(Long arrendadorId, TipoDocumentoIdentidad tipo, String rutaArchivo,
                                               String tipoMime, int tamano, Instant subidoEn) {
        var documento = new DocumentoIdentidad();
        documento.arrendadorId = arrendadorId;
        documento.tipo = tipo;
        documento.rutaArchivo = rutaArchivo;
        documento.tipoMime = tipoMime;
        documento.tamano = tamano;
        documento.subidoEn = subidoEn;
        return documento;
    }

    public Long getId() {
        return id;
    }

    public Long getArrendadorId() {
        return arrendadorId;
    }

    public TipoDocumentoIdentidad getTipo() {
        return tipo;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public int getTamano() {
        return tamano;
    }

    public Instant getSubidoEn() {
        return subidoEn;
    }
}
