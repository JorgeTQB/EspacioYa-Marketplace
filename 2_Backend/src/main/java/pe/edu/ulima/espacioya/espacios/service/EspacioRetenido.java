package pe.edu.ulima.espacioya.espacios.service;

import java.math.BigDecimal;

/** Datos del espacio leídos bajo su bloqueo de fila, para reservar sin una segunda lectura (HU-12 DT-2). */
public record EspacioRetenido(String nombre, BigDecimal precioHora) {
}
