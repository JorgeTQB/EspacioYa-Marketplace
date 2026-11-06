package pe.edu.ulima.espacioya.espacios.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Ficha de un espacio ya validada y con los textos recortados. {@code caracteristicas} nunca es null (HU-23).
 */
public record DatosEspacio(String nombre, TipoEspacio tipo, String direccion, String zona, int capacidad,
                           BigDecimal precioHora, String descripcion, Set<Caracteristica> caracteristicas) {

    public DatosEspacio {
        caracteristicas = caracteristicas == null ? Set.of() : Set.copyOf(caracteristicas);
    }

    /** Ficha de HU-07 sin características (HU-23 DT-15). */
    public DatosEspacio(String nombre, TipoEspacio tipo, String direccion, String zona, int capacidad,
                        BigDecimal precioHora, String descripcion) {
        this(nombre, tipo, direccion, zona, capacidad, precioHora, descripcion, Set.of());
    }
}
