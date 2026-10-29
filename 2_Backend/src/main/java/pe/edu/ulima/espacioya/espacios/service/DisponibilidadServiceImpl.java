package pe.edu.ulima.espacioya.espacios.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.espacios.domain.Bloque;
import pe.edu.ulima.espacioya.espacios.domain.CalculoDisponibilidad;
import pe.edu.ulima.espacioya.espacios.domain.EstadoBloque;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.HorarioSemanal;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.repository.BloqueoFechaRepository;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.HorarioSemanalRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;

/**
 * Bloques de un día calculados al consultar: horario − bloqueos − reservas (HU-08 DT-8). El puerto de reservas
 * llega como {@link ObjectProvider}: hasta que HU-12 lo implemente no hay bean y ninguna fecha tiene reservas (R21).
 */
@Service
public class DisponibilidadServiceImpl implements DisponibilidadService {

    private final EspacioRepository espacios;
    private final HorarioSemanalRepository horarios;
    private final BloqueoFechaRepository bloqueos;
    private final ObjectProvider<ReservasDelEspacio> reservas;
    private final Clock reloj;

    public DisponibilidadServiceImpl(EspacioRepository espacios, HorarioSemanalRepository horarios,
                                     BloqueoFechaRepository bloqueos, ObjectProvider<ReservasDelEspacio> reservas,
                                     Clock reloj) {
        this.espacios = espacios;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.reservas = reservas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> horasLibres(Long espacioId, LocalDate fecha) {
        if (!ventana().contiene(fecha)) {
            return List.of();
        }
        var publicado = espacios.findById(espacioId)
                .filter(espacio -> espacio.getEstado() == EstadoEspacio.PUBLICADO)
                .isPresent();
        if (!publicado) {
            return List.of();
        }
        return bloquesDelDia(espacioId, fecha).stream()
                .filter(bloque -> bloque.estado() == EstadoBloque.LIBRE)
                .map(Bloque::horaInicio)
                .toList();
    }

    VentanaReserva ventana() {
        return VentanaReserva.desde(ZonaHoraria.hoy(reloj));
    }

    List<BloqueReservado> reservasDelDia(Long espacioId, LocalDate fecha) {
        var puerto = reservas.getIfAvailable();
        if (puerto == null) {
            return List.of();
        }
        return puerto.bloquesReservados(espacioId, fecha, fecha);
    }

    List<Bloque> bloquesDelDia(Long espacioId, LocalDate fecha) {
        var franjas = horarios.findByEspacioIdAndDiaSemanaOrderByHoraInicioAsc(espacioId,
                        (short) fecha.getDayOfWeek().getValue()).stream()
                .map(HorarioSemanal::aFranja)
                .toList();
        var bloqueada = bloqueos.existsByEspacioIdAndFecha(espacioId, fecha);
        var horasReservadas = bloqueada ? Set.<Integer>of()
                : reservasDelDia(espacioId, fecha).stream()
                        .filter(reservado -> fecha.equals(reservado.fecha()))
                        .map(BloqueReservado::horaInicio)
                        .collect(Collectors.toSet());
        return CalculoDisponibilidad.bloquesDelDia(fecha, franjas, bloqueada, horasReservadas,
                ZonaHoraria.ahora(reloj));
    }
}
