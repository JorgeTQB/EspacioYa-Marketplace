package pe.edu.ulima.espacioya.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.EstadoReembolso;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.ReglasLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.dto.PaginaTransaccionesDTO;
import pe.edu.ulima.espacioya.pagos.dto.TransaccionDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.comprobante.DatosComprobante;
import pe.edu.ulima.espacioya.pagos.service.comprobante.GeneradorComprobantePdf;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaDelUsuario;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasDelUsuario;

/**
 * "Mis pagos" y comprobantes del usuario (HU-28). El usuario sale siempre de la sesión. La propiedad la garantiza el
 * puerto {@link ReservasDelUsuario}: {@code pagos} no lee la tabla {@code reserva}. Solo lectura (R22).
 */
@Service
public class HistorialPagosService {

    public static final int TAMANO_PAGINA = 10;
    public static final String CAMPO_PAGINA = "pagina";
    public static final String MENSAJE_PAGINA = "La página no es válida.";

    private static final String ROL_USUARIO = "USUARIO";
    private static final String PASARELA_SIMULADA = "simulada";
    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");
    private static final Pattern ID_DE_PAGO = Pattern.compile("[0-9]{1,18}");

    private final PagoRepository pagos;
    private final ReembolsoRepository reembolsos;
    private final ReservasDelUsuario reservas;
    private final GeneradorComprobantePdf generador;
    private final boolean incluirSimulados;

    public HistorialPagosService(PagoRepository pagos, ReembolsoRepository reembolsos, ReservasDelUsuario reservas,
                                 GeneradorComprobantePdf generador,
                                 @Value("${espacioya.pagos.pasarela}") String pasarela) {
        this.pagos = pagos;
        this.reembolsos = reembolsos;
        this.reservas = reservas;
        this.generador = generador;
        // El mismo criterio que GeneradorLiquidaciones y DatosPersonalesPagosImpl.
        this.incluirSimulados = PASARELA_SIMULADA.equalsIgnoreCase(pasarela == null ? "" : pasarela.strip());
    }

    /** R1 a R9: pagos {@code PAGADO} y {@code REEMBOLSADO} del usuario de la sesión, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public PaginaTransaccionesDTO listar(UsuarioAutenticado usuario, String pagina) {
        exigirUsuario(usuario);
        var numero = numeroDePagina(pagina);
        if (numero == 0) {
            throw new ValidacionException(List.of(new ErrorCampoDTO(CAMPO_PAGINA, MENSAJE_PAGINA)));
        }
        Map<Long, ReservaDelUsuario> suyas = reservas.todas(usuario.usuarioId()).stream()
                .collect(Collectors.toMap(ReservaDelUsuario::reservaId, Function.identity()));
        if (suyas.isEmpty()) {
            return new PaginaTransaccionesDTO(numero, List.of(), false);
        }
        var slice = pagos.deReservas(suyas.keySet(), EstadoPago.EN_HISTORIAL, incluirSimulados,
                PageRequest.of(numero - 1, TAMANO_PAGINA));
        var deLaPagina = slice.getContent();
        var reembolsados = reembolsadoPorPago(deLaPagina.stream().map(Pago::getId).toList());
        var filas = deLaPagina.stream()
                .map(pago -> fila(pago, suyas.get(pago.getReservaId()), reembolsados))
                .toList();
        return new PaginaTransaccionesDTO(numero, filas, slice.hasNext());
    }

    /** R11 a R20: el PDF de un pago propio y elegible; cualquier otro caso es el mismo 404. */
    @Transactional(readOnly = true)
    public ComprobanteDescargable comprobante(UsuarioAutenticado usuario, String id) {
        exigirUsuario(usuario);
        if (id == null || !ID_DE_PAGO.matcher(id).matches() || Long.parseLong(id) <= 0) {
            throw new RecursoNoEncontradoException();
        }
        var pago = pagos.findById(Long.parseLong(id)).orElseThrow(RecursoNoEncontradoException::new);
        if (!EstadoPago.EN_HISTORIAL.contains(pago.getEstado())
                || (esSimulado(pago) && !incluirSimulados)) {
            throw new RecursoNoEncontradoException();
        }
        var reserva = reservas.una(usuario.usuarioId(), pago.getReservaId())
                .orElseThrow(RecursoNoEncontradoException::new);
        var reembolsado = reembolsadoPorPago(List.of(pago.getId()));
        var datos = new DatosComprobante(pago.getId(), fechaDe(pago), reserva.espacio(), reserva.fecha(),
                reserva.horaInicio(), reserva.horaFin(), pago.getMonto(), pago.getEstado().name(),
                montoReembolsado(pago, reembolsado), pago.getUltimos4(), esSimulado(pago));
        return new ComprobanteDescargable("comprobante-" + pago.getId() + ".pdf", generador.generar(datos));
    }

    private static void exigirUsuario(UsuarioAutenticado usuario) {
        if (usuario == null || !ROL_USUARIO.equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private static TransaccionDTO fila(Pago pago, ReservaDelUsuario reserva, Map<Long, BigDecimal> reembolsado) {
        return new TransaccionDTO(pago.getId(), pago.getReservaId(), fechaDe(pago), reserva.espacio(), pago.getMonto(),
                pago.getEstado().name(), montoReembolsado(pago, reembolsado), esSimulado(pago));
    }

    private static LocalDate fechaDe(Pago pago) {
        return LocalDate.ofInstant(pago.getCreadoEn(), ZonaHoraria.LIMA);
    }

    private static boolean esSimulado(Pago pago) {
        return ReglasLiquidacion.esSimulado(pago.getIdTransaccionPasarela());
    }

    private static BigDecimal montoReembolsado(Pago pago, Map<Long, BigDecimal> reembolsado) {
        if (pago.getEstado() != EstadoPago.REEMBOLSADO) {
            return null;
        }
        return reembolsado.getOrDefault(pago.getId(), BigDecimal.ZERO);
    }

    // Una consulta por lote (sin N+1): suma de los reembolsos REEMBOLSADO de cada pago.
    private Map<Long, BigDecimal> reembolsadoPorPago(List<Long> pagoIds) {
        if (pagoIds.isEmpty()) {
            return Map.of();
        }
        return reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(pagoIds).stream()
                .filter(reembolso -> reembolso.getEstado() == EstadoReembolso.REEMBOLSADO)
                .collect(Collectors.groupingBy(Reembolso::getPagoId,
                        Collectors.reducing(BigDecimal.ZERO, Reembolso::getMonto, BigDecimal::add)));
    }

    // Criterio de HU-10, HU-14 y HU-19: sin parámetro, la página 1; 0 = no válida (no solo dígitos o fuera de int).
    private static int numeroDePagina(String texto) {
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
}
