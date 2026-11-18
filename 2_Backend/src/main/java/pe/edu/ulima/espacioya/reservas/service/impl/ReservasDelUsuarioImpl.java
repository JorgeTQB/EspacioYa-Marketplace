package pe.edu.ulima.espacioya.reservas.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaDelUsuario;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasDelUsuario;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;

/**
 * Reservas del usuario para el historial de pagos (HU-28). Lee siempre con {@link ReservaDAO#delUsuario}, que exige el
 * dueño en la firma: una reserva ajena no aparece (R8, R17). Solo lee.
 */
@Service
@Transactional(readOnly = true)
public class ReservasDelUsuarioImpl implements ReservasDelUsuario {

    static final String ESPACIO_SIN_NOMBRE = "Espacio";

    private final ReservaDAO reservas;
    private final CatalogoEspaciosService catalogo;

    public ReservasDelUsuarioImpl(ReservaDAO reservas, CatalogoEspaciosService catalogo) {
        this.reservas = reservas;
        this.catalogo = catalogo;
    }

    @Override
    public List<ReservaDelUsuario> todas(Long usuarioId) {
        return describir(reservas.delUsuario(usuarioId));
    }

    @Override
    public Optional<ReservaDelUsuario> una(Long usuarioId, Long reservaId) {
        var suya = reservas.delUsuario(usuarioId).stream()
                .filter(conHorario -> conHorario.reserva().getId().equals(reservaId))
                .toList();
        return describir(suya).stream().findFirst();
    }

    private List<ReservaDelUsuario> describir(List<ReservaConHorario> suyas) {
        if (suyas.isEmpty()) {
            return List.of();
        }
        var espacioIds = suyas.stream().map(r -> r.reserva().getEspacioId()).distinct().toList();
        Map<Long, String> nombres = catalogo.nombres(espacioIds);
        return suyas.stream()
                .map(r -> new ReservaDelUsuario(r.reserva().getId(), r.reserva().getEspacioId(),
                        nombres.getOrDefault(r.reserva().getEspacioId(), ESPACIO_SIN_NOMBRE), r.fecha(),
                        r.horaInicio(), r.horaFin()))
                .toList();
    }
}
