package pe.edu.ulima.espacioya.busqueda.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Promedio con 1 decimal y total de reseñas visibles; se calcula al consultar. Sin reseñas, promedio nulo. */
public record ResumenCalificaciones(BigDecimal promedio, long total) {

    private static final int DECIMALES = 1;

    /** Con enteros y no con {@code avg}: un {@code double} puede redondear mal 4.25 (DT-11). */
    public static ResumenCalificaciones de(long cantidad, long suma) {
        if (cantidad == 0) {
            return new ResumenCalificaciones(null, 0);
        }
        var promedio = BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(cantidad), DECIMALES, RoundingMode.HALF_UP);
        return new ResumenCalificaciones(promedio, cantidad);
    }
}
