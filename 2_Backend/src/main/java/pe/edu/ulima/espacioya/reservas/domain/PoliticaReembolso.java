package pe.edu.ulima.espacioya.reservas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Strategy (★ HU-15): una regla fija de RN-06 para el reembolso de una cancelación. Cada regla es una clase que dice
 * si {@link #aplicaA} una solicitud y con qué porcentaje; {@link CalculoReembolso} (Context) elige la única que aplica,
 * así el servicio no tiene ningún {@code if} sobre quién cancela ni sobre las 48 horas.
 */
public interface PoliticaReembolso {

    Duration ANTICIPACION_MINIMA = Duration.ofHours(48);

    String codigo();

    boolean aplicaA(SolicitudCancelacion solicitud);

    int porcentaje();

    /** {@code montoPagado × porcentaje / 100}, escala 2, {@code HALF_UP} (R16). */
    default BigDecimal monto(BigDecimal montoPagado) {
        return montoPagado.multiply(BigDecimal.valueOf(porcentaje()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
