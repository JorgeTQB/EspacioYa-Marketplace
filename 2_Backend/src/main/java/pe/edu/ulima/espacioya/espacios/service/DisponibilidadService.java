package pe.edu.ulima.espacioya.espacios.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Interfaz pública de {@code espacios} para {@code busqueda} y {@code reservas} (HU-10, HU-11, HU-12). */
public interface DisponibilidadService {

    String MENSAJE_HORARIO_NO_DISPONIBLE = "Horario no disponible. Elige otro bloque.";

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

    /**
     * Bloquea la fila del espacio hasta el fin de la transacción del llamador, sin filtrar por dueño, y comprueba bajo
     * ese bloqueo que las horas siguen libres (HU-08 DT-10, HU-12 DT-2). Debe ser la primera lectura del espacio en la
     * transacción: nombre y precio se leen ya bloqueados. La fecha la valida el llamador.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay una transacción activa.
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si el espacio no existe, no está
     *         PUBLICADO o su arrendador no está ACTIVA.
     * @throws pe.edu.ulima.espacioya.comun.error.ReglaNegocioException si alguna hora no es un bloque libre.
     */
    EspacioRetenido retenerSiLibres(Long espacioId, LocalDate fecha, List<Integer> horas);
}
