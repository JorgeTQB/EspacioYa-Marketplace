package pe.edu.ulima.espacioya.reservas.repository;

import java.util.Collection;
import java.util.List;

import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;

/**
 * DAO de reservas con su horario (★ HU-14, primer DAO explícito del repo). Toda consulta exige su dueño como primer
 * parámetro: no hay forma de pedir reservas sin él (STRIDE E/I, RNF-SEG-19). Esconde que la reserva y su horario viven
 * en dos tablas: una consulta a {@code reserva}, otra a {@code reserva_bloque} y la unión en memoria (R35). No filtra
 * por tiempo: el estado se lee con {@code Reserva.estadoEn} (DT-6).
 */
public interface ReservaDAO {

    /** Las reservas con ese {@code usuario_id}, en cualquier estado, cada una con su horario (R33). */
    List<ReservaConHorario> delUsuario(Long usuarioId);

    /**
     * Las reservas {@code CONFIRMADA} cuyo {@code espacio_id} está en la colección, cada una con su horario; vacía sin
     * consultar si la colección está vacía (R34).
     */
    List<ReservaConHorario> confirmadasDeEspacios(Collection<Long> espacioIds);
}
