package pe.edu.ulima.espacioya.espacios.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lista fija de características de un espacio (HU-23 CA1, RF-B-08), en el orden en que se muestran. Los códigos
 * coinciden con las filas sembradas en la tabla {@code caracteristica} por {@code V7_1__caracteristicas.sql}.
 */
public enum Caracteristica {
    TECHADO, ILUMINACION, VESTUARIOS, ESTACIONAMIENTO;

    /** true solo si el texto es exactamente uno de los códigos (sensible a mayúsculas); false con null. */
    public static boolean esCodigo(String texto) {
        return texto != null && Arrays.stream(values()).anyMatch(valor -> valor.name().equals(texto));
    }

    /** Códigos ya validados → conjunto inmodificable en el orden del enum; colección vacía → conjunto vacío. */
    public static Set<Caracteristica> deCodigos(Collection<String> codigos) {
        var conjunto = EnumSet.noneOf(Caracteristica.class);
        codigos.forEach(codigo -> conjunto.add(valueOf(codigo)));
        return Collections.unmodifiableSet(conjunto);
    }

    /** "[TECHADO, VESTUARIOS]" en el orden del enum; "[]" si está vacío (auditoría, R7). */
    public static String describir(Set<Caracteristica> caracteristicas) {
        var ordenadas = caracteristicas.isEmpty()
                ? EnumSet.noneOf(Caracteristica.class)
                : EnumSet.copyOf(caracteristicas);
        return ordenadas.stream().map(Enum::name).collect(Collectors.joining(", ", "[", "]"));
    }
}
