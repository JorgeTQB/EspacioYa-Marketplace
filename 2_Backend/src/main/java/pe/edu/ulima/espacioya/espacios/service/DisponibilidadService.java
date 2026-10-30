package pe.edu.ulima.espacioya.espacios.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Interfaz pública de {@code espacios} para {@code busqueda} y {@code reservas} (HU-10, HU-11, HU-12). */
public interface DisponibilidadService {

    /**
     * Horas de inicio (0–23) de los bloques LIBRE del espacio en la fecha, ascendentes. Vacía si el espacio no
     * existe, no está PUBLICADO, su arrendador no está ACTIVA (HU-09) o la fecha está fuera de la ventana de 8
     * semanas.
     */
    List<Integer> horasLibres(Long espacioId, LocalDate fecha);

    /**
     * Ids de {@code espacioIds} para los que {@link #horasLibres} no es vacía (HU-10 R30). Vacío si la colección está
     * vacía o la fecha está fuera de la ventana (R32). Consulta horario, bloqueos y reservas una vez por lote (R31).
     */
    Set<Long> conBloquesLibres(Collection<Long> espacioIds, LocalDate fecha);
}
