package pe.edu.ulima.espacioya.administracion.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.administracion.domain.MensajeReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;
import pe.edu.ulima.espacioya.administracion.dto.ReclamoExportadoDTO;
import pe.edu.ulima.espacioya.administracion.repository.MensajeReclamoRepository;
import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

/**
 * Participante de {@code administracion} en HU-22 (HU-26 P10, § 11). Exporta solo los textos del titular, impide
 * eliminar la cuenta con un reclamo abierto y, al eliminarla, reemplaza sus textos y conserva todo lo demás.
 */
@Component
@Order(4)
public class DatosPersonalesReclamosImpl implements ParticipanteDatosPersonales {

    public static final String SECCION_RECLAMOS = "reclamos";
    public static final String CAMPO_RECLAMOS = "reclamos";
    public static final String MENSAJE_RECLAMO_ABIERTO =
            "Tienes un reclamo abierto. Espera a que se resuelva antes de eliminar tu cuenta.";

    private final ReclamoRepository reclamos;
    private final MensajeReclamoRepository mensajes;
    private final ReservasParaReclamo reservas;

    public DatosPersonalesReclamosImpl(ReclamoRepository reclamos, MensajeReclamoRepository mensajes,
                                       ReservasParaReclamo reservas) {
        this.reclamos = reclamos;
        this.mensajes = mensajes;
        this.reservas = reservas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeccionDatos> exportar(TitularDatos titular) {
        if (!titular.esUsuario() && !titular.esArrendador()) {
            return List.of();
        }
        var id = titular.usuarioId();
        var suyos = reclamos.findByUsuarioIdOrArrendadorIdOrderByAbiertoEnAscIdAsc(id, id);
        var misMensajes = mensajes.findByAutorIdOrderByFechaAscIdAsc(id).stream()
                .collect(Collectors.groupingBy(MensajeReclamo::getReclamoId,
                        Collectors.mapping(MensajeReclamo::getTexto, Collectors.toList())));
        Map<Long, ReservaParaReclamo> descritas = suyos.isEmpty() ? Map.of()
                : reservas.describir(suyos.stream().map(Reclamo::getReservaId).toList()).stream()
                        .collect(Collectors.toMap(ReservaParaReclamo::id, Function.identity()));
        var exportados = suyos.stream().map(reclamo -> {
            var textos = new ArrayList<String>();
            if (id.equals(reclamo.getUsuarioId())) {
                textos.add(reclamo.getDescripcion());
            }
            textos.addAll(misMensajes.getOrDefault(reclamo.getId(), List.of()));
            var reserva = descritas.get(reclamo.getReservaId());
            return new ReclamoExportadoDTO(reclamo.getReservaId(), reserva == null ? null : reserva.espacioNombre(),
                    reclamo.getMotivo().name(), reclamo.getEstado().name(), reclamo.getAbiertoEn(),
                    reclamo.getResueltoEn(), reclamo.aFavorDe().map(Enum::name).orElse(null), List.copyOf(textos));
        }).toList();
        return List.of(new SeccionDatos(SECCION_RECLAMOS, exportados));
    }

    /** R69: sin bloqueos (design § 15, riesgo aceptado). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Impedimento> impedimentos(TitularDatos titular, Instant ahora) {
        if (reclamos.existeAbiertoDe(titular.usuarioId())) {
            return List.of(new Impedimento(CAMPO_RECLAMOS, MENSAJE_RECLAMO_ABIERTO));
        }
        return List.of();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void anonimizar(TitularDatos titular) {
        reclamos.reemplazarDescripciones(titular.usuarioId(), Reclamo.TEXTO_ELIMINADO);
        mensajes.reemplazarTextos(titular.usuarioId(), Reclamo.TEXTO_ELIMINADO);
    }
}
