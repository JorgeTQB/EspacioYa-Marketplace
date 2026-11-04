package pe.edu.ulima.espacioya.reservas.service;

import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.reservas.dto.CrearReservaDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;

/** Interfaz pública de {@code reservas} (docs/architecture.md, principio 5). */
public interface ReservaService {

    /**
     * Crea una reserva {@code PENDIENTE_PAGO} a nombre del usuario de la sesión y retiene sus bloques 15 minutos.
     *
     * @throws pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException si el rol de la sesión no es {@code USUARIO}.
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si el espacio no es visible.
     * @throws pe.edu.ulima.espacioya.comun.error.ReglaNegocioException si la fecha o la selección no son válidas, se
     *         superó el cupo de 3 pendientes o alguna hora no es un bloque libre.
     */
    ReservaDTO crear(UsuarioAutenticado usuario, CrearReservaDTO datos, String ip);

    /**
     * Reserva del usuario de la sesión. Una vencida se lee {@code EXPIRADA} aunque nadie la haya expirado aún.
     *
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si el id está mal escrito, no existe o
     *         es de otro usuario.
     */
    ReservaDTO consultar(UsuarioAutenticado usuario, String id);
}
