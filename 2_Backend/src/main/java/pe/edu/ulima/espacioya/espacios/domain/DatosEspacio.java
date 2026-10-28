package pe.edu.ulima.espacioya.espacios.domain;

import java.math.BigDecimal;

/**
 * Ficha de un espacio ya validada y con los textos recortados.
 */
public record DatosEspacio(String nombre, TipoEspacio tipo, String direccion, String zona, int capacidad,
                           BigDecimal precioHora, String descripcion) {
}
