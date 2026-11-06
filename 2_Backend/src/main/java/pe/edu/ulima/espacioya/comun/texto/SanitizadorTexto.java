package pe.edu.ulima.espacioya.comun.texto;

import java.util.regex.Pattern;

/**
 * Limpia un texto libre antes de guardarlo (RNF-SEG-10, HU-24 DT-8). No escapa a entidades: quedarían {@code &lt;} a la
 * vista en la ficha y en la exportación de HU-22, y React ya escapa al mostrar. Las expresiones no retroceden: costo
 * lineal.
 */
public final class SanitizadorTexto {

    private static final Pattern SALTO_DE_LINEA = Pattern.compile("\\r\\n?");
    // U+0000 además hace fallar al guardar en PostgreSQL.
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern ETIQUETA = Pattern.compile("<[^<>]*>");
    private static final Pattern ANGULAR = Pattern.compile("[<>]");

    private SanitizadorTexto() {
    }

    /** El resultado nunca tiene {@code <} ni {@code >}, ni con {@code <scr<script>ipt>}; puede quedar vacío. */
    public static String limpiar(String texto) {
        var normalizado = SALTO_DE_LINEA.matcher(texto).replaceAll("\n");
        var sinControl = CONTROL.matcher(normalizado).replaceAll("");
        var sinEtiquetas = ETIQUETA.matcher(sinControl).replaceAll("");
        return ANGULAR.matcher(sinEtiquetas).replaceAll("").strip();
    }
}
