package pe.edu.ulima.espacioya.administracion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.administracion.domain.MensajeReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;
import pe.edu.ulima.espacioya.administracion.domain.ReglasReclamo;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReclamoAdminDTO;
import pe.edu.ulima.espacioya.administracion.dto.DetalleReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.MensajeReclamoDTO;
import pe.edu.ulima.espacioya.administracion.dto.ReclamoResumenDTO;
import pe.edu.ulima.espacioya.administracion.repository.MensajeReclamoRepository;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.service.PagoDeReserva;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

/**
 * Arma los DTO de reclamos para las partes y para el admin (HU-26 § 7). Se une a la transacción del servicio que lo
 * llama. La reserva se describe por lote ({@link ReservasParaReclamo#describir}): las partes ya se validaron con la
 * fila de {@code reclamo}.
 */
@Component
public class VistaReclamos {

    static final String ESTADO_PAGADO = "PAGADO";
    private static final Set<String> PAGOS_COBRADOS = Set.of(ESTADO_PAGADO, "REEMBOLSADO", "REEMBOLSO_FALLIDO");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");

    private final ReservasParaReclamo reservas;
    private final MensajeReclamoRepository mensajes;
    private final CuentaService cuentas;
    private final PagoService pagos;

    public VistaReclamos(ReservasParaReclamo reservas, MensajeReclamoRepository mensajes, CuentaService cuentas,
                         PagoService pagos) {
        this.reservas = reservas;
        this.mensajes = mensajes;
        this.cuentas = cuentas;
        this.pagos = pagos;
    }

    /** Sin parámetro, la página 1. 0 = página no válida (fuera de 1..Integer.MAX_VALUE o no solo dígitos), R34. */
    public static int numeroDePagina(String texto) {
        if (texto == null) {
            return 1;
        }
        if (!SOLO_DIGITOS.matcher(texto).matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** La reserva del reclamo, descrita sin dueño; vacía solo si la reserva no tiene bloques. */
    public Optional<ReservaParaReclamo> reservaDe(Reclamo reclamo) {
        return reservas.describir(List.of(reclamo.getReservaId())).stream().findFirst();
    }

    public DetalleReclamoDTO paraParte(Reclamo reclamo, PapelReclamo papel, Long cuentaId) {
        return paraParte(reclamo, reservaDe(reclamo).orElse(null), papel, cuentaId);
    }

    // Al arrendador nunca le llega el monto (R32, P12).
    public DetalleReclamoDTO paraParte(Reclamo reclamo, ReservaParaReclamo reserva, PapelReclamo papel,
                                       Long cuentaId) {
        var hilo = hiloDe(reclamo);
        var propios = hilo.stream().filter(mensaje -> mensaje.getAutorId().equals(cuentaId)).count();
        var restantes = (int) Math.max(0, ReglasReclamo.MAXIMO_MENSAJES_POR_PARTE - propios);
        var monto = papel == PapelReclamo.USUARIO ? reclamo.getMontoReembolso() : null;
        return new DetalleReclamoDTO(reclamo.getId(), reclamo.getReservaId(), nombre(reserva), fecha(reserva),
                horaInicio(reserva), horaFin(reserva), reclamo.getMotivo().name(), reclamo.getDescripcion(),
                reclamo.getEstado().name(), reclamo.getAbiertoEn(), aDtos(hilo), aFavorDe(reclamo),
                reclamo.getResolucion(), reclamo.getResueltoEn(), monto, papel.name(), restantes,
                reclamo.estaAbierto() && restantes > 0);
    }

    public DetalleReclamoAdminDTO paraAdmin(Reclamo reclamo) {
        return paraAdmin(reclamo, reservaDe(reclamo).orElse(null));
    }

    // Solo nombres: nunca correo, teléfono ni datos de la tarjeta (R37).
    public DetalleReclamoAdminDTO paraAdmin(Reclamo reclamo, ReservaParaReclamo reserva) {
        var nombres = cuentas.nombres(List.of(reclamo.getUsuarioId(), reclamo.getArrendadorId()));
        var pagado = pagos.pagosDeReservas(List.of(reclamo.getReservaId())).stream()
                .filter(pago -> PAGOS_COBRADOS.contains(pago.estado()))
                .map(PagoDeReserva::monto)
                .findFirst()
                .orElse(null);
        return new DetalleReclamoAdminDTO(reclamo.getId(), reclamo.getReservaId(), nombre(reserva), fecha(reserva),
                horaInicio(reserva), horaFin(reserva), reclamo.getMotivo().name(), reclamo.getDescripcion(),
                reclamo.getEstado().name(), reclamo.getAbiertoEn(), aDtos(hiloDe(reclamo)), aFavorDe(reclamo),
                reclamo.getResolucion(), reclamo.getResueltoEn(), reclamo.getMontoReembolso(),
                nombres.get(reclamo.getUsuarioId()), nombres.get(reclamo.getArrendadorId()), pagado);
    }

    /** Monto del pago {@code PAGADO} de la reserva, si lo hay (HU-13: como mucho uno). */
    public Optional<BigDecimal> montoPagado(Long reservaId) {
        return pagos.pagosDeReservas(List.of(reservaId)).stream()
                .filter(pago -> ESTADO_PAGADO.equals(pago.estado()))
                .map(PagoDeReserva::monto)
                .findFirst();
    }

    /** Una consulta por lote para toda la página: sin N+1. */
    public List<ReclamoResumenDTO> resumenes(List<Reclamo> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, ReservaParaReclamo> descritas = reservas.describir(lista.stream().map(Reclamo::getReservaId)
                        .toList()).stream()
                .collect(Collectors.toMap(ReservaParaReclamo::id, Function.identity()));
        return lista.stream().map(reclamo -> {
            var reserva = descritas.get(reclamo.getReservaId());
            return new ReclamoResumenDTO(reclamo.getId(), reclamo.getReservaId(), nombre(reserva), fecha(reserva),
                    reclamo.getMotivo().name(), reclamo.getEstado().name(), reclamo.getAbiertoEn(),
                    reclamo.getResueltoEn());
        }).toList();
    }

    public static MensajeReclamoDTO aDto(MensajeReclamo mensaje) {
        return new MensajeReclamoDTO(mensaje.getId(), mensaje.getPapel().name(), mensaje.getTexto(),
                mensaje.getFecha());
    }

    private List<MensajeReclamo> hiloDe(Reclamo reclamo) {
        return mensajes.findByReclamoIdOrderByFechaAscIdAsc(reclamo.getId());
    }

    private static List<MensajeReclamoDTO> aDtos(List<MensajeReclamo> hilo) {
        return hilo.stream().map(VistaReclamos::aDto).toList();
    }

    private static String aFavorDe(Reclamo reclamo) {
        return reclamo.aFavorDe().map(Enum::name).orElse(null);
    }

    private static String nombre(ReservaParaReclamo reserva) {
        return reserva == null ? null : reserva.espacioNombre();
    }

    private static LocalDate fecha(ReservaParaReclamo reserva) {
        return reserva == null ? null : reserva.fecha();
    }

    private static int horaInicio(ReservaParaReclamo reserva) {
        return reserva == null ? 0 : reserva.horaInicio();
    }

    private static int horaFin(ReservaParaReclamo reserva) {
        return reserva == null ? 0 : reserva.horaFin();
    }
}
