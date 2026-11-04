package pe.edu.ulima.espacioya.busqueda.service;

import java.util.List;

import org.springframework.stereotype.Service;

import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/** Espacios visibles del tipo y la zona con al menos un bloque libre en la fecha (HU-10 CA1), en el orden recibido. */
@Service
public class BusquedaBase implements BusquedaEspacios {

    private final CatalogoEspaciosService catalogo;
    private final DisponibilidadService disponibilidad;

    public BusquedaBase(CatalogoEspaciosService catalogo, DisponibilidadService disponibilidad) {
        this.catalogo = catalogo;
        this.disponibilidad = disponibilidad;
    }

    @Override
    public List<EspacioPublicado> buscar(CriteriosBusqueda criterios) {
        var candidatos = catalogo.publicados(criterios.tipo(), criterios.zona());
        if (candidatos.isEmpty()) {
            return List.of();
        }
        var libres = disponibilidad.conBloquesLibres(candidatos.stream().map(EspacioPublicado::id).toList(),
                criterios.fecha());
        return candidatos.stream().filter(espacio -> libres.contains(espacio.id())).toList();
    }
}
