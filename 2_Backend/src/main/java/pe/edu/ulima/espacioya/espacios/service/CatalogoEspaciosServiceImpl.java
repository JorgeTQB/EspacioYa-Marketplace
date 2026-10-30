package pe.edu.ulima.espacioya.espacios.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Zona;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.ZonaRepository;

@Service
public class CatalogoEspaciosServiceImpl implements CatalogoEspaciosService {

    private static final short ORDEN_FOTO_PRINCIPAL = 1;

    private final EspacioRepository espacios;
    private final ZonaRepository zonas;
    private final FotoEspacioRepository fotos;
    private final CuentaService cuentas;

    public CatalogoEspaciosServiceImpl(EspacioRepository espacios, ZonaRepository zonas, FotoEspacioRepository fotos,
                                       CuentaService cuentas) {
        this.espacios = espacios;
        this.zonas = zonas;
        this.fotos = fotos;
        this.cuentas = cuentas;
    }

    @Override
    public boolean esTipoValido(String tipo) {
        return tipo != null && Arrays.stream(TipoEspacio.values()).anyMatch(t -> t.name().equals(tipo));
    }

    @Override
    public boolean existeZona(String codigo) {
        return codigo != null && !codigo.isBlank() && zonas.existsById(codigo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspacioPublicado> publicados(String tipo, String zona) {
        if (!esTipoValido(tipo)) {
            throw new IllegalArgumentException("Tipo de espacio no válido: " + tipo);
        }
        var tipoEspacio = TipoEspacio.valueOf(tipo);
        // Los espacios de un arrendador no ACTIVA conservan su estado pero no se muestran (HU-09 D1); con el
        // conjunto vacío se omite la condición, porque "not in" con una colección vacía no es portable en JPQL.
        var excluidos = cuentas.arrendadoresNoActivos();
        var encontrados = excluidos.isEmpty()
                ? espacios.buscarPublicados(EstadoEspacio.PUBLICADO, tipoEspacio, zona)
                : espacios.buscarPublicadosExcluyendo(EstadoEspacio.PUBLICADO, tipoEspacio, zona, excluidos);
        if (encontrados.isEmpty()) {
            return List.of();
        }
        var zonaNombre = zonas.findById(zona).map(Zona::getNombre).orElse(zona);
        return encontrados.stream()
                .map(espacio -> new EspacioPublicado(espacio.getId(), espacio.getNombre(), espacio.getTipo().name(),
                        espacio.getZona(), zonaNombre, espacio.getPrecioHora()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> fotosPrincipales(Collection<Long> espacioIds) {
        if (espacioIds.isEmpty()) {
            return Map.of();
        }
        return fotos.findByEspacioIdInAndOrden(espacioIds, ORDEN_FOTO_PRINCIPAL).stream()
                .collect(Collectors.toMap(FotoEspacio::getEspacioId, EspacioService::urlDe, (a, b) -> a));
    }
}
