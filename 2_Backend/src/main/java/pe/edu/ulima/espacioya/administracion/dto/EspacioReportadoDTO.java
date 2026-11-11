package pe.edu.ulima.espacioya.administracion.dto;

import java.math.BigDecimal;

/** El espacio de un reporte en su estado actual (HU-27 R24). Del arrendador solo el nombre, sin correo ni teléfono. */
public record EspacioReportadoDTO(String nombre, String tipo, String zona, String direccion, int capacidad,
                                  BigDecimal precioHora, String descripcion, String estado, String arrendadorNombre) {
}
