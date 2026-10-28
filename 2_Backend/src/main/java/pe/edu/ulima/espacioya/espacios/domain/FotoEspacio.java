package pe.edu.ulima.espacioya.espacios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Foto de un espacio. El contenido vive sin cifrar en el almacenamiento: aquí solo está su clave aleatoria
 * ({@code espacios/<UUID>}), nunca el nombre original. La principal es la de {@code orden = 1}.
 */
@Entity
@Table(name = "foto_espacio")
public class FotoEspacio {

    public static final int ORDEN_MAXIMO = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "espacio_id", nullable = false)
    private Long espacioId;

    @Column(name = "ruta_archivo", nullable = false, length = 100)
    private String rutaArchivo;

    @Column(name = "tipo_mime", nullable = false, length = 30)
    private String tipoMime;

    @Column(nullable = false)
    private short orden;

    protected FotoEspacio() {
    }

    public static FotoEspacio registrar(Long espacioId, String rutaArchivo, String tipoMime, int orden) {
        var foto = new FotoEspacio();
        foto.espacioId = espacioId;
        foto.rutaArchivo = rutaArchivo;
        foto.tipoMime = tipoMime;
        foto.moverA(orden);
        return foto;
    }

    public void moverA(int nuevoOrden) {
        if (nuevoOrden < 1 || nuevoOrden > ORDEN_MAXIMO) {
            throw new IllegalArgumentException("El orden de una foto va de 1 a " + ORDEN_MAXIMO);
        }
        orden = (short) nuevoOrden;
    }

    public Long getId() {
        return id;
    }

    public Long getEspacioId() {
        return espacioId;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public int getOrden() {
        return orden;
    }
}
