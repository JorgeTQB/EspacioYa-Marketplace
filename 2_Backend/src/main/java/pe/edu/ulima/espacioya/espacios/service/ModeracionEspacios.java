package pe.edu.ulima.espacioya.espacios.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;

/**
 * Interfaz pública de {@code espacios} para la moderación de {@code administracion} (HU-27 DT-1). No es un puerto:
 * {@code administracion → espacios} está permitida. Sin sesión salvo {@link #ocultar}.
 */
public interface ModeracionEspacios {

    /** readOnly. Vacío si el id es {@code null}, no existe o no es visible (PUBLICADO y arrendador ACTIVA). */
    Optional<EspacioReportable> reportable(Long espacioId);

    /** readOnly. Cualquier estado; los que no existen no aparecen; colección vacía → {@code Map.of()} sin consultar. */
    Map<Long, EspacioModerado> describir(Collection<Long> espacioIds);

    /**
     * MANDATORY. Rol ADMIN antes de leer → bloqueo de la fila (404) → PUBLICADO o PAUSADO (400 no ocultable) →
     * OCULTO → auditoría {@code OCULTACION_ESPACIO}. No mira ni cambia reservas (DT-11).
     */
    EspacioOcultado ocultar(UsuarioAutenticado admin, Long espacioId, String ip);
}
