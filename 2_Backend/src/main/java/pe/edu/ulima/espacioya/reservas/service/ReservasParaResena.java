package pe.edu.ulima.espacioya.reservas.service;

import java.util.List;

/**
 * Interfaz pública de {@code reservas} para las reseñas de {@code busqueda} (HU-24 DT-2). No es un puerto: la dirección
 * {@code busqueda → reservas} ya está permitida. Sin sesión ni rol: el llamador pasa el id de la sesión.
 */
public interface ReservasParaResena {

    /** @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si no es una reserva de ese usuario. */
    ReservaParaResena delUsuario(Long usuarioId, Long reservaId);

    /** Las de estado leído CONFIRMADA de ese usuario, cualquiera sea su fin. */
    List<ReservaParaResena> confirmadasDe(Long usuarioId);
}
