package pe.edu.ulima.espacioya.administracion.domain;

import java.time.Duration;
import java.time.Instant;

/** Cupo de reportes por reportante (HU-27 P5, RNF-SEG-17): 10 en una ventana móvil de 24 h. */
public final class ReglasReporte {

    public static final int MAXIMO_POR_DIA = 10;
    public static final Duration VENTANA = Duration.ofHours(24);

    private ReglasReporte() {
    }

    /** Inicio exclusivo de la ventana: cuentan los reportes con {@code creado_en > ahora − 24 h} (R17). */
    public static Instant inicioVentana(Instant ahora) {
        return ahora.minus(VENTANA);
    }
}
