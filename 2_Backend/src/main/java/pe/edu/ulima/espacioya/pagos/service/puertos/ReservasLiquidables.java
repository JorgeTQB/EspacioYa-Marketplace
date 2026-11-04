package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.util.Collection;
import java.util.List;

/**
 * Lo que {@code pagos} necesita de las reservas para liquidarlas (HU-19). Lo define {@code pagos} y lo implementa
 * {@code reservas} (DIP), así {@code reservas} no conoce {@code liquidacion} y {@code pagos} no conoce
 * {@code reserva_bloque} ni los estados de la reserva. La regla de las 48 h vive en {@code pagos}.
 */
public interface ReservasLiquidables {

    /**
     * Una por cada id que existe y tiene horario; las demás no aparecen. Colección vacía → {@code List.of()} sin
     * consultar. Sin sesión ni rol: lo llama {@code pagos} con ids que salen de sus propios pagos.
     */
    List<ReservaLiquidable> describir(Collection<Long> reservaIds);
}
