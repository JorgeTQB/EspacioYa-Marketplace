package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.util.Collection;
import java.util.Set;

/**
 * Puerto de {@code pagos} que implementa {@code administracion} (RN-12, HU-26 DT-4): qué reservas están retenidas por
 * un reclamo abierto. {@code administracion} ya usa {@code PagoService}; sin el puerto habría un ciclo.
 */
public interface ReclamosAbiertos {

    /** Ids, de entre esos, con un reclamo ABIERTO. Vacía → {@code Set.of()} sin consultar. Se une a la transacción. */
    Set<Long> conReclamoAbierto(Collection<Long> reservaIds);
}
