package pe.edu.ulima.espacioya.reservas.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.espacios.domain.VentanaReserva;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.dto.PagoExportadoDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaExportadaDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;

/**
 * Participante de {@code reservas} en HU-22. Exporta las reservas del titular y, para el usuario, sus pagos (DT-3:
 * {@code pagos} no sabe qué reservas son suyas). Las reservas son registros contables: no se anonimizan (R31).
 */
@Component
@Order(1)
public class DatosPersonalesReservasImpl implements ParticipanteDatosPersonales {

    public static final String SECCION_RESERVAS = "reservas";
    public static final String SECCION_PAGOS = "pagos";
    public static final String CAMPO_RESERVAS = "reservas";
    public static final String MENSAJE_RESERVAS_USUARIO = "Tienes reservas próximas o en curso. Cancélalas o espera "
            + "a que terminen antes de eliminar tu cuenta.";
    public static final String MENSAJE_RESERVAS_ARRENDADOR = "Tus espacios tienen reservas por terminar. Cancélalas "
            + "o espera a que terminen antes de eliminar tu cuenta.";

    private static final Comparator<ReservaConHorario> POR_INICIO = Comparator
            .comparing(ReservaConHorario::inicio)
            .thenComparing(r -> r.reserva().getId());

    private final ReservaDAO reservas;
    private final CatalogoEspaciosService catalogo;
    private final DisponibilidadService disponibilidad;
    private final ReservasDelEspacio reservasDelEspacio;
    private final PagoService pagos;
    private final Clock reloj;

    public DatosPersonalesReservasImpl(ReservaDAO reservas, CatalogoEspaciosService catalogo,
                                       DisponibilidadService disponibilidad, ReservasDelEspacio reservasDelEspacio,
                                       PagoService pagos, Clock reloj) {
        this.reservas = reservas;
        this.catalogo = catalogo;
        this.disponibilidad = disponibilidad;
        this.reservasDelEspacio = reservasDelEspacio;
        this.pagos = pagos;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeccionDatos> exportar(TitularDatos titular) {
        if (titular.esUsuario()) {
            return exportarDelUsuario(titular.usuarioId());
        }
        if (titular.esArrendador()) {
            return List.of(new SeccionDatos(SECCION_RESERVAS, exportarDelArrendador(titular.usuarioId())));
        }
        return List.of();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Impedimento> impedimentos(TitularDatos titular, Instant ahora) {
        var ahoraLocal = LocalDateTime.ofInstant(ahora, ZonaHoraria.LIMA);
        if (titular.esUsuario() && tieneReservasProximas(titular.usuarioId(), ahora, ahoraLocal)) {
            return List.of(new Impedimento(CAMPO_RESERVAS, MENSAJE_RESERVAS_USUARIO));
        }
        if (titular.esArrendador() && tieneReservasPorTerminar(titular.usuarioId(), ahoraLocal)) {
            return List.of(new Impedimento(CAMPO_RESERVAS, MENSAJE_RESERVAS_ARRENDADOR));
        }
        return List.of();
    }

    /** Nada: {@code reserva} solo guarda {@code usuario_id} y es un registro contable (R31). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void anonimizar(TitularDatos titular) {
        // Sin datos personales propios que suprimir.
    }

    private List<SeccionDatos> exportarDelUsuario(Long usuarioId) {
        var ahora = Instant.now(reloj);
        var suyas = reservas.delUsuario(usuarioId).stream().sorted(POR_INICIO).toList();
        var nombres = catalogo.nombres(suyas.stream().map(r -> r.reserva().getEspacioId()).distinct().toList());
        var exportadas = suyas.stream().map(r -> delUsuario(r, nombres, ahora)).toList();
        var pagosExportados = pagos.pagosDeReservas(suyas.stream().map(r -> r.reserva().getId()).toList()).stream()
                .map(PagoExportadoDTO::de)
                .toList();
        return List.of(new SeccionDatos(SECCION_RESERVAS, exportadas),
                new SeccionDatos(SECCION_PAGOS, pagosExportados));
    }

    // Como HU-14 para el arrendador: solo CONFIRMADA y nunca quién reservó (R7, R13).
    private List<ReservaExportadaDTO> exportarDelArrendador(Long arrendadorId) {
        var espacios = catalogo.nombresDelArrendador(arrendadorId);
        return reservas.confirmadasDeEspacios(espacios.keySet()).stream()
                .sorted(POR_INICIO)
                .map(r -> new ReservaExportadaDTO(r.reserva().getId(), espacios.get(r.reserva().getEspacioId()),
                        r.fecha(), r.horaInicio(), r.horaFin(), r.reserva().getEstado().name(), null,
                        r.reserva().getMontoTotal(), null, null, null, null))
                .toList();
    }

    private static ReservaExportadaDTO delUsuario(ReservaConHorario conHorario, Map<Long, String> nombres,
                                                  Instant ahora) {
        var reserva = conHorario.reserva();
        return new ReservaExportadaDTO(reserva.getId(), nombres.get(reserva.getEspacioId()), conHorario.fecha(),
                conHorario.horaInicio(), conHorario.horaFin(), reserva.estadoEn(ahora).name(),
                reserva.getPrecioHoraAplicado(), reserva.getMontoTotal(), reserva.getCreadaEn(),
                reserva.getCanceladaPor() == null ? null : reserva.getCanceladaPor().name(), reserva.getCanceladaEn(),
                reserva.getMontoReembolso());
    }

    private boolean tieneReservasProximas(Long usuarioId, Instant ahora, LocalDateTime ahoraLocal) {
        return reservas.delUsuario(usuarioId).stream().anyMatch(r -> r.esProxima(ahora, ahoraLocal));
    }

    /**
     * R43: por cada espacio, en orden de id, primero su bloqueo y después sus reservas. Crear una reserva relee la
     * cuenta del arrendador bajo el mismo bloqueo: o la ve esta eliminación, o encuentra la cuenta ya eliminada.
     */
    private boolean tieneReservasPorTerminar(Long arrendadorId, LocalDateTime ahoraLocal) {
        var ventana = VentanaReserva.desde(LocalDate.from(ahoraLocal));
        var espacioIds = catalogo.nombresDelArrendador(arrendadorId).keySet().stream().sorted().toList();
        for (var espacioId : espacioIds) {
            disponibilidad.bloquearEspacio(espacioId);
            var porTerminar = reservasDelEspacio.bloquesReservados(espacioId, ventana.desde(), ventana.hasta())
                    .stream()
                    .anyMatch(bloque -> bloque.terminaDespuesDe(ahoraLocal));
            if (porTerminar) {
                return true;
            }
        }
        return false;
    }
}
