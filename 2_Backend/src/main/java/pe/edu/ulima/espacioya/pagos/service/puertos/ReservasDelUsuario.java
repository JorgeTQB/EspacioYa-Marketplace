package pe.edu.ulima.espacioya.pagos.service.puertos;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de HU-28: {@code pagos} no sabe de quién es una reserva, así que se lo pregunta a {@code reservas}, que lo
 * implementa. El dueño va siempre en la firma: una reserva ajena simplemente no aparece (STRIDE E/I). Sin sesión ni
 * rol: el llamador pasa el id de la sesión.
 */
public interface ReservasDelUsuario {

    /** Todas las reservas del usuario con su espacio y horario. */
    List<ReservaDelUsuario> todas(Long usuarioId);

    /** Una reserva del usuario; vacío si es ajena o no existe. */
    Optional<ReservaDelUsuario> una(Long usuarioId, Long reservaId);
}
