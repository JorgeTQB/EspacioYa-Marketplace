package pe.edu.ulima.espacioya.espacios.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Espacio de un arrendador (HU-07). Nace en {@code BORRADOR}; solo {@link #publicar} lo lleva a
 * {@code PUBLICADO} y editarlo nunca cambia su estado.
 */
@Entity
@Table(name = "espacio")
public class Espacio {

    private static final int DECIMALES_PRECIO = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arrendador_id", nullable = false)
    private Long arrendadorId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEspacio tipo;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Column(nullable = false, length = 40)
    private String zona;

    @Column(nullable = false)
    private int capacidad;

    @Column(name = "precio_hora", nullable = false, precision = 8, scale = 2)
    private BigDecimal precioHora;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEspacio estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Espacio() {
    }

    public static Espacio crear(Long arrendadorId, DatosEspacio datos, Instant ahora) {
        var espacio = new Espacio();
        espacio.arrendadorId = arrendadorId;
        espacio.asignar(datos);
        espacio.estado = EstadoEspacio.BORRADOR;
        espacio.creadoEn = ahora;
        espacio.actualizadoEn = ahora;
        return espacio;
    }

    /**
     * Aplica la ficha y devuelve qué cambió, en orden fijo, como {@code campo=anterior->nuevo}; la descripción
     * va solo con su nombre por su largo. Si nada cambió, la lista está vacía y {@code actualizadoEn} no se toca.
     */
    public List<String> editar(DatosEspacio datos, Instant ahora) {
        if (!esEditable()) {
            throw new IllegalStateException("El espacio no se puede editar en estado " + estado);
        }
        var nuevoPrecio = escalar(datos.precioHora());
        var cambios = new ArrayList<String>();
        anotar(cambios, "nombre", nombre, datos.nombre());
        anotar(cambios, "tipo", tipo, datos.tipo());
        anotar(cambios, "direccion", direccion, datos.direccion());
        anotar(cambios, "zona", zona, datos.zona());
        anotar(cambios, "capacidad", capacidad, datos.capacidad());
        if (precioHora.compareTo(nuevoPrecio) != 0) {
            cambios.add("precioHora=" + precioHora.toPlainString() + "->" + nuevoPrecio.toPlainString());
        }
        if (!descripcion.equals(datos.descripcion())) {
            cambios.add("descripcion");
        }
        if (!cambios.isEmpty()) {
            asignar(datos);
            actualizadoEn = ahora;
        }
        return List.copyOf(cambios);
    }

    public void publicar(Instant ahora) {
        if (!esPublicable()) {
            throw new IllegalStateException("Solo se publica un espacio en borrador, no en " + estado);
        }
        estado = EstadoEspacio.PUBLICADO;
        actualizadoEn = ahora;
    }

    public void tocar(Instant ahora) {
        actualizadoEn = ahora;
    }

    public boolean esEditable() {
        return estado == EstadoEspacio.BORRADOR || estado == EstadoEspacio.PUBLICADO
                || estado == EstadoEspacio.PAUSADO;
    }

    public boolean esPublicable() {
        return estado == EstadoEspacio.BORRADOR;
    }

    private void asignar(DatosEspacio datos) {
        nombre = datos.nombre();
        tipo = datos.tipo();
        direccion = datos.direccion();
        zona = datos.zona();
        capacidad = datos.capacidad();
        precioHora = escalar(datos.precioHora());
        descripcion = datos.descripcion();
    }

    // RN-18: un único precio por hora, en soles y con 2 decimales. Un monto nunca se redondea en silencio.
    private static BigDecimal escalar(BigDecimal precio) {
        return precio.setScale(DECIMALES_PRECIO, RoundingMode.UNNECESSARY);
    }

    private static void anotar(List<String> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + "=" + anterior + "->" + nuevo);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getArrendadorId() {
        return arrendadorId;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoEspacio getTipo() {
        return tipo;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getZona() {
        return zona;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public BigDecimal getPrecioHora() {
        return precioHora;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public EstadoEspacio getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
