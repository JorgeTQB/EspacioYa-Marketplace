package pe.edu.ulima.espacioya.administracion.service.impl;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReclamosAbiertos;

/**
 * Implementación del puerto de {@code pagos} (HU-26 DT-4): lee la fuente de verdad, la tabla {@code reclamo}, sin
 * bloquear. Lo llama la generación de liquidaciones después de tomar su bloqueo consultivo.
 */
@Component
public class ReclamosAbiertosImpl implements ReclamosAbiertos {

    private final ReclamoRepository reclamos;

    public ReclamosAbiertosImpl(ReclamoRepository reclamos) {
        this.reclamos = reclamos;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> conReclamoAbierto(Collection<Long> reservaIds) {
        if (reservaIds == null || reservaIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(reclamos.reservasConReclamoAbierto(reservaIds));
    }
}
