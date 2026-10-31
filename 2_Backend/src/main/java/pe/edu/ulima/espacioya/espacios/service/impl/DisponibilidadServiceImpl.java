package pe.edu.ulima.espacioya.espacios.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.Bloque;
import pe.edu.ulima.espacioya.espacios.domain.CalculoDisponibilidad;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoBloque;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FranjaHoraria;
import pe.edu.ulima.espacioya.espacios.domain.HorarioSemanal;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.repository.BloqueoFechaRepository;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.HorarioSemanalRepository;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioBloqueado;
import pe.edu.ulima.espacioya.espacios.service.EspacioRetenido;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;

/**
 * Bloques de un día calculados al consultar: horario − bloqueos − reservas (HU-08 DT-8). El puerto de reservas
 * llega como {@link ObjectProvider}: sin su bean, ninguna fecha tiene reservas (R21).
 */
@Service
public class DisponibilidadServiceImpl implements DisponibilidadService {

    private final EspacioRepository espacios;
    private final HorarioSemanalRepository horarios;
    private final BloqueoFechaRepository bloqueos;
    private final ObjectProvider<ReservasDelEspacio> reservas;
    private final CuentaService cuentas;
    private final Clock reloj;

    public DisponibilidadServiceImpl(EspacioRepository espacios, HorarioSemanalRepository horarios,
                                     BloqueoFechaRepository bloqueos, ObjectProvider<ReservasDelEspacio> reservas,
                                     CuentaService cuentas, Clock reloj) {
        this.espacios = espacios;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.reservas = reservas;
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> horasLibres(Long espacioId, LocalDate fecha) {
        if (!ventana().contiene(fecha)) {
            return List.of();
        }
        var publicado = espacios.findById(espacioId)
                .filter(espacio -> espacio.getEstado() == EstadoEspacio.PUBLICADO);
        // El espacio de un arrendador no ACTIVA conserva su estado pero no ofrece horas (HU-09 D1, R23).
        if (publicado.isEmpty() || !cuentas.cuentaActiva(publicado.get().getArrendadorId())) {
            return List.of();
        }
        return bloquesDelDia(espacioId, fecha).stream()
                .filter(bloque -> bloque.estado() == EstadoBloque.LIBRE)
                .map(Bloque::horaInicio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> conBloquesLibres(Collection<Long> espacioIds, LocalDate fecha) {
        if (espacioIds.isEmpty() || !ventana().contiene(fecha)) {
            return Set.of();
        }
        var noActivos = cuentas.arrendadoresNoActivos();
        var visibles = espacios.findAllById(Set.copyOf(espacioIds)).stream()
                .filter(espacio -> espacio.getEstado() == EstadoEspacio.PUBLICADO)
                .filter(espacio -> !noActivos.contains(espacio.getArrendadorId()))
                .map(Espacio::getId)
                .toList();
        if (visibles.isEmpty()) {
            return Set.of();
        }
        Map<Long, List<FranjaHoraria>> franjas = horarios
                .findByEspacioIdInAndDiaSemana(visibles, (short) fecha.getDayOfWeek().getValue()).stream()
                .collect(Collectors.groupingBy(HorarioSemanal::getEspacioId,
                        Collectors.mapping(HorarioSemanal::aFranja, Collectors.toList())));
        if (franjas.isEmpty()) {
            return Set.of();
        }
        var candidatos = new HashSet<>(franjas.keySet());
        candidatos.removeAll(Set.copyOf(bloqueos.espaciosBloqueados(candidatos, fecha)));
        if (candidatos.isEmpty()) {
            return Set.of();
        }
        var puerto = reservas.getIfAvailable();
        Map<Long, List<BloqueReservado>> reservadas = puerto == null ? Map.of()
                : puerto.bloquesReservadosDelDia(candidatos, fecha);
        var ahora = ZonaHoraria.ahora(reloj);
        var conLibres = new HashSet<Long>();
        for (var id : candidatos) {
            var horasReservadas = reservadas.getOrDefault(id, List.of()).stream()
                    .filter(reservado -> fecha.equals(reservado.fecha()))
                    .map(BloqueReservado::horaInicio)
                    .collect(Collectors.toSet());
            var bloques = CalculoDisponibilidad.bloquesDelDia(fecha, franjas.get(id), false, horasReservadas, ahora);
            if (bloques.stream().anyMatch(bloque -> bloque.estado() == EstadoBloque.LIBRE)) {
                conLibres.add(id);
            }
        }
        return conLibres;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EspacioRetenido retenerSiLibres(Long espacioId, LocalDate fecha, List<Integer> horas) {
        var espacio = espacios.findParaReservar(espacioId)
                .filter(encontrado -> encontrado.getEstado() == EstadoEspacio.PUBLICADO)
                .filter(encontrado -> cuentas.cuentaActiva(encontrado.getArrendadorId()))
                .orElseThrow(RecursoNoEncontradoException::new);
        var libres = bloquesDelDia(espacioId, fecha).stream()
                .filter(bloque -> bloque.estado() == EstadoBloque.LIBRE)
                .map(Bloque::horaInicio)
                .collect(Collectors.toSet());
        if (!libres.containsAll(horas)) {
            throw new ReglaNegocioException(MENSAJE_HORARIO_NO_DISPONIBLE);
        }
        return new EspacioRetenido(espacio.getNombre(), espacio.getPrecioHora());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EspacioBloqueado bloquearEspacio(Long espacioId) {
        var espacio = espacios.findParaReservar(espacioId).orElseThrow(RecursoNoEncontradoException::new);
        return new EspacioBloqueado(espacio.getArrendadorId(), espacio.getNombre());
    }

    public VentanaReserva ventana() {
        return VentanaReserva.desde(ZonaHoraria.hoy(reloj));
    }

    public List<BloqueReservado> reservasDelDia(Long espacioId, LocalDate fecha) {
        var puerto = reservas.getIfAvailable();
        if (puerto == null) {
            return List.of();
        }
        return puerto.bloquesReservados(espacioId, fecha, fecha);
    }

    public List<Bloque> bloquesDelDia(Long espacioId, LocalDate fecha) {
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
