package pe.edu.ulima.espacioya.reservas.domain;

import java.math.BigDecimal;

/** Resultado del Strategy (HU-15): el código de la política elegida, su porcentaje y el monto a reembolsar. */
public record ReembolsoCalculado(String politica, int porcentaje, BigDecimal monto) {
}
