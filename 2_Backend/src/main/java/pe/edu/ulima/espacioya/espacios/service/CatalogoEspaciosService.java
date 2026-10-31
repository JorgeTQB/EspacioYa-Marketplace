package pe.edu.ulima.espacioya.espacios.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interfaz pública del catálogo de {@code espacios} para {@code busqueda} (HU-10, HU-11, HU-23) y {@code reservas}
 * (HU-12, HU-14). Sin sesión.
 */
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

    /**
     * Ficha de un espacio PUBLICADO de arrendador ACTIVA (HU-09 D1), con las URL de sus fotos por orden ascendente;
     * vacío si el id es {@code null}, no existe o el espacio no es visible (HU-11 R31, R32).
     */
    Optional<FichaPublicada> publicado(Long espacioId);

    /** {@code true} solo si el espacio existe, está PUBLICADO y su arrendador está ACTIVA (HU-11 R33). */
    boolean esVisible(Long espacioId);

    /**
     * Id del espacio → nombre, sin filtro de visibilidad: una reserva ya creada sigue mostrando su espacio aunque este
     * se haya pausado o su arrendador esté suspendido (HU-12 S11). Los ids que no existen no aparecen.
     */
    Map<Long, String> nombres(Collection<Long> espacioIds);

    /**
     * Id → nombre de todos los espacios del arrendador, en cualquier estado y sin filtro de visibilidad (HU-14 CA2);
     * vacío si el id es {@code null} o no tiene espacios. Sin sesión ni rol: el llamador pasa el id de la sesión.
     */
    Map<Long, String> nombresDelArrendador(Long arrendadorId);
}
