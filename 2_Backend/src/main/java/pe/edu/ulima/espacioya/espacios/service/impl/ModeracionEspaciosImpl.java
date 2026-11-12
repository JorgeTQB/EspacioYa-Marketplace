package pe.edu.ulima.espacioya.espacios.service.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.EspacioModerado;
import pe.edu.ulima.espacioya.espacios.service.EspacioOcultado;
import pe.edu.ulima.espacioya.espacios.service.EspacioReportable;
import pe.edu.ulima.espacioya.espacios.service.EstadoEspacioService;
import pe.edu.ulima.espacioya.espacios.service.ModeracionEspacios;

/**
 * Moderación de espacios para {@code administracion} (HU-27). El cambio de estado vive en
 * {@link EstadoEspacioService}.
 */
@Service
public class ModeracionEspaciosImpl implements ModeracionEspacios {

    private final CatalogoEspaciosService catalogo;
    private final EspacioRepository espacios;
    private final EstadoEspacioService estados;

    public ModeracionEspaciosImpl(CatalogoEspaciosService catalogo, EspacioRepository espacios,
                                  EstadoEspacioService estados) {
        this.catalogo = catalogo;
        this.espacios = espacios;
        this.estados = estados;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EspacioReportable> reportable(Long espacioId) {
        // La misma regla que la ficha pública (HU-11 R33): lo que el público no ve no se puede reportar (R7).
        if (espacioId == null || !catalogo.esVisible(espacioId)) {
            return Optional.empty();
        }
        return espacios.findById(espacioId).map(espacio -> new EspacioReportable(espacio.getId(),
                espacio.getArrendadorId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EspacioModerado> describir(Collection<Long> espacioIds) {
        if (espacioIds.isEmpty()) {
            return Map.of();
        }
        return espacios.findAllById(Set.copyOf(espacioIds)).stream()
                .map(ModeracionEspaciosImpl::moderado)
                .collect(Collectors.toMap(EspacioModerado::id, Function.identity()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EspacioOcultado ocultar(UsuarioAutenticado admin, Long espacioId, String ip) {
        return estados.ocultarComoAdmin(admin, espacioId, ip);
    }

    private static EspacioModerado moderado(Espacio espacio) {
        return new EspacioModerado(espacio.getId(), espacio.getArrendadorId(), espacio.getNombre(),
                espacio.getTipo().name(), espacio.getZona(), espacio.getDireccion(), espacio.getCapacidad(),
                espacio.getPrecioHora(), espacio.getDescripcion(), espacio.getEstado().name());
    }
}
