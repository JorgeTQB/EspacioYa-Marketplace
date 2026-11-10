package pe.edu.ulima.espacioya.reservas.service;

import java.util.Collection;
import java.util.List;

/**
 * Interfaz pública de {@code reservas} para los reclamos de {@code administracion} (HU-26 DT-2). No es un puerto: la
 * dirección {@code administracion → reservas} ya está permitida. Sin sesión ni rol: el llamador pasa el id de la
 * sesión o ids que ya validó.
 */
public interface ReservasParaReclamo {

    /**
     * La reserva de ese usuario. {@code readOnly}.
     *
     * @throws pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException si no es una reserva de ese usuario.
     */
    ReservaParaReclamo delUsuario(Long usuarioId, Long reservaId);

    /** Las de estado leído CONFIRMADA de ese usuario, cualquiera sea su horario. {@code readOnly}. */
    List<ReservaParaReclamo> confirmadasDe(Long usuarioId);

    /**
     * Consulta del sistema por lote, sin dueño: las partes ya se validaron con la fila de {@code reclamo}. Colección
     * vacía → {@code List.of()} sin consultar; una reserva sin bloques no aparece. {@code readOnly}.
     */
    List<ReservaParaReclamo> describir(Collection<Long> reservaIds);
}
