package pe.edu.ulima.espacioya.espacios.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Interfaz pública del catálogo de {@code espacios} para {@code busqueda} (HU-10, HU-11, HU-23). Sin sesión. */
public interface CatalogoEspaciosService {

    /** {@code true} solo si el texto es exactamente CANCHA, ESTADIO o AUDITORIO. */
    boolean esTipoValido(String tipo);

    /** {@code true} solo si existe una zona con ese código exacto. */
    boolean existeZona(String codigo);

    /**
     * Espacios PUBLICADO de arrendadores ACTIVA del tipo y la zona, por precio por hora y luego id ascendentes.
     * {@link IllegalArgumentException} si el tipo no es válido.
     */
    List<EspacioPublicado> publicados(String tipo, String zona);

    /** Id del espacio → URL {@code /api/fotos/<UUID>} de su foto con orden 1; los que no la tienen no aparecen. */
    Map<Long, String> fotosPrincipales(Collection<Long> espacioIds);
}
