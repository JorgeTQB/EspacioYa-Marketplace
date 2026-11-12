package pe.edu.ulima.espacioya.busqueda.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;

/**
 * Interfaz pública de {@code busqueda} para la moderación de {@code administracion} (HU-27 DT-1). No es un puerto:
 * {@code administracion → busqueda} está permitida. Sin sesión salvo {@link #ocultar}.
 */
public interface ModeracionResenas {

    /** readOnly. Vacío si el id es {@code null}, no existe, está OCULTA o su espacio no es visible. */
    Optional<ResenaReportable> reportable(Long resenaId);

    /** readOnly. Cualquier estado; colección vacía → {@code Map.of()} sin consultar. */
    Map<Long, ResenaModerada> describir(Collection<Long> resenaIds);

    /**
     * MANDATORY. Rol ADMIN antes de leer → bloqueo de la fila (404) → VISIBLE (400 no ocultable) → OCULTA →
     * auditoría {@code OCULTACION_RESENA}.
     */
    void ocultar(UsuarioAutenticado admin, Long resenaId, String ip);
}
