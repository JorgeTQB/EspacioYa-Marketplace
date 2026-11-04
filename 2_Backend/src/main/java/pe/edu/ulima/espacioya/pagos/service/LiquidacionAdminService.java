package pe.edu.ulima.espacioya.pagos.service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.MotivoNoPagable;
import pe.edu.ulima.espacioya.pagos.domain.ReglasPago;
import pe.edu.ulima.espacioya.pagos.dto.GeneracionLiquidacionesDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDetalleDTO;
import pe.edu.ulima.espacioya.pagos.dto.MarcarLiquidacionPagadaDTO;
import pe.edu.ulima.espacioya.pagos.dto.PaginaLiquidacionesDTO;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;

/**
 * Rutas del admin de HU-19: generar, listar, ver el detalle y marcar como pagada. {@code FiltroIpsAdmin} ya exigió una
 * IP permitida y la sesión de admin trae MFA (HU-17); aquí se exige el rol {@code ADMIN} antes de leer nada (R2).
 * Quien actúa y su IP salen de la sesión y de la conexión; montos, estados y fechas, del servidor (STRIDE T).
 */
@Service
public class LiquidacionAdminService {

    public static final String ACCION_GENERADA = "LIQUIDACION_GENERADA";
    public static final String ACCION_PAGADA = "LIQUIDACION_PAGADA";
    public static final String ACCION_CUENTA_CONSULTADA = "CUENTA_BANCARIA_CONSULTADA_ADMIN";
    public static final int TAMANO_PAGINA = 20;

    public static final String MENSAJE_SIN_CUENTA = "El arrendador aún no registra una cuenta bancaria. Podrás marcar "
            + "esta liquidación como pagada cuando la registre.";
    public static final String MENSAJE_CUENTA_RETENIDA = "La cuenta bancaria del arrendador cambió hace poco. Podrás "
            + "marcar esta liquidación como pagada desde el %s.";
    public static final String MENSAJE_YA_PAGADA = "Esta liquidación ya está pagada.";
    public static final String MENSAJE_GENERACION_CRUZADA =
            "Otra generación de liquidaciones se cruzó con esta. Vuelve a cargar la lista.";
    public static final String MENSAJE_GRUPO = "Elige por pagar o pagadas.";
    public static final String MENSAJE_PAGINA = "La página no es válida.";
    public static final String CAMPO_GRUPO = "grupo";
    public static final String CAMPO_PAGINA = "pagina";
    public static final String GRUPO_POR_PAGAR = "por-pagar";
    public static final String GRUPO_PAGADAS = "pagadas";

    static final String RESTRICCION_RESERVA_LIQUIDADA = "uq_liquidacion_detalle_reserva";
    private static final String ROL_ADMIN = "ADMIN";
    private static final String ENTIDAD_LIQUIDACION = "liquidacion";
    private static final String ENTIDAD_CUENTA = "cuenta_bancaria";
    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZonaHoraria.LIMA);

    private final LiquidacionRepository liquidaciones;
    private final CuentaBancariaRepository cuentas;
    private final GeneradorLiquidaciones generador;
    private final VistaLiquidaciones vista;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public LiquidacionAdminService(LiquidacionRepository liquidaciones, CuentaBancariaRepository cuentas,
                                   GeneradorLiquidaciones generador, VistaLiquidaciones vista,
                                   AuditoriaService auditoria, Clock reloj) {
        this.liquidaciones = liquidaciones;
        this.cuentas = cuentas;
        this.generador = generador;
        this.vista = vista;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** CA1, CA3: liquida todo lo pendiente al corte. Sin nada que liquidar responde la lista vacía (R18). */
    @Transactional
    public GeneracionLiquidacionesDTO generar(UsuarioAutenticado admin, String ip) {
        exigirAdmin(admin);
        GeneradorLiquidaciones.Generacion generacion;
        try {
            generacion = generador.generar(admin.usuarioId(), ip);
        } catch (DataIntegrityViolationException conflicto) {
            // Última defensa (DT-7): con el bloqueo no debería ocurrir; se revierte toda la generación.
            if (nombra(conflicto, RESTRICCION_RESERVA_LIQUIDADA)) {
                throw new ReglaNegocioException(MENSAJE_GENERACION_CRUZADA);
            }
            throw conflicto;
        }
        return new GeneracionLiquidacionesDTO(generacion.corte(), vista.resumenes(generacion.creadas()));
    }

    /** {@code por-pagar} (por defecto) de la más antigua a la más reciente; {@code pagadas} al revés (S9). */
    @Transactional(readOnly = true)
    public PaginaLiquidacionesDTO<LiquidacionAdminDTO> listar(UsuarioAutenticado admin, String grupo,
                                                              String pagina) {
        exigirAdmin(admin);
        var errores = new ArrayList<ErrorCampoDTO>();
        var pagadas = GRUPO_PAGADAS.equals(grupo);
        if (grupo != null && !pagadas && !GRUPO_POR_PAGAR.equals(grupo)) {
            errores.add(new ErrorCampoDTO(CAMPO_GRUPO, MENSAJE_GRUPO));
        }
        var numero = numeroDePagina(pagina);
        if (numero == 0) {
            errores.add(new ErrorCampoDTO(CAMPO_PAGINA, MENSAJE_PAGINA));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        var slice = pagadas
                ? liquidaciones.findByEstadoIn(List.of(EstadoLiquidacion.PAGADA),
                        PageRequest.of(numero - 1, TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "pagadaEn", "id")))
                : liquidaciones.findByEstadoIn(EstadoLiquidacion.POR_PAGAR,
                        PageRequest.of(numero - 1, TAMANO_PAGINA, Sort.by("generadaEn", "id")));
        return new PaginaLiquidacionesDTO<>(numero, vista.resumenes(slice.getContent()), slice.hasNext());
    }

    /**
     * Detalle con las líneas y la cuenta vigente (R36). Si es pagable entrega el número completo para transferir y lo
     * audita en la misma transacción (D4, R38, R39); si no, solo enmascarado (R40).
     */
    @Transactional
    public LiquidacionAdminDetalleDTO detalle(UsuarioAutenticado admin, String id, String ip) {
        exigirAdmin(admin);
        var liquidacion = liquidaciones.findById(idOr404(id)).orElseThrow(RecursoNoEncontradoException::new);
        var ahora = Instant.now(reloj);
        var cuenta = cuentas.findByArrendadorId(liquidacion.getArrendadorId());
        var pagable = liquidacion.motivoNoPagable(cuenta, ahora).isEmpty();
        if (pagable) {
            var vigente = cuenta.orElseThrow();
            auditoria.registrar(new EventoAuditoria(admin.usuarioId(), ACCION_CUENTA_CONSULTADA, ENTIDAD_CUENTA,
                    vigente.getId(), ip, "liquidacionId=" + liquidacion.getId() + ";arrendadorId="
                    + liquidacion.getArrendadorId() + ";cuenta=" + vigente.enmascarado()));
        }
        return vista.detalle(liquidacion, cuenta, pagable, ahora);
    }

    /**
     * CA2: la deja PAGADA con el número de operación (R25, R26). Bloquea {@code liquidacion → cuenta_bancaria} y toma
     * <i>ahora</i> después (DT-8). En los 400 no escribe nada (R27–R29, R32).
     */
    @Transactional
    public LiquidacionAdminDetalleDTO marcarPagada(UsuarioAutenticado admin, String id,
                                                   MarcarLiquidacionPagadaDTO solicitud, String ip) {
        exigirAdmin(admin);
        var liquidacion = liquidaciones.findParaActualizar(idOr404(id))
                .orElseThrow(RecursoNoEncontradoException::new);
        var ahora = Instant.now(reloj);
        var cuenta = cuentas.findParaActualizar(liquidacion.getArrendadorId());
        var motivo = liquidacion.motivoNoPagable(cuenta, ahora);
        if (motivo.isPresent()) {
            throw new ReglaNegocioException(mensaje(motivo.get(), cuenta.orElse(null)));
        }
        var anterior = liquidacion.getEstado();
        var destino = cuenta.orElseThrow();
        liquidacion.marcarPagada(solicitud.numeroOperacion(), destino, admin.usuarioId(), ahora);
        liquidaciones.saveAndFlush(liquidacion);
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), ACCION_PAGADA, ENTIDAD_LIQUIDACION,
                liquidacion.getId(), ip, "estadoAnterior=" + anterior.name() + ";numeroOperacion="
                + liquidacion.getNumeroOperacion() + ";cuenta=" + liquidacion.getBancoDestino() + "/"
                + liquidacion.getCuentaDestino() + ";neto=" + liquidacion.getMontoNeto().toPlainString()
                + ";simulada=" + liquidacion.isSimulada()));
        return vista.detalle(liquidacion, cuenta, false, ahora);
    }

    private static String mensaje(MotivoNoPagable motivo, CuentaBancaria cuenta) {
        return switch (motivo) {
            case YA_PAGADA -> MENSAJE_YA_PAGADA;
            case SIN_CUENTA -> MENSAJE_SIN_CUENTA;
            case CUENTA_RETENIDA -> String.format(MENSAJE_CUENTA_RETENIDA,
                    FECHA_HORA.format(cuenta.getRetenerHasta()));
        };
    }

    private static Long idOr404(String id) {
        var numero = ReglasPago.idBienEscrito(id);
        if (numero == null) {
            throw new RecursoNoEncontradoException();
        }
        return numero;
    }

    // Criterio de HU-10 y HU-14: sin parámetro, la página 1; 0 = no válida (no solo dígitos o fuera de int).
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

    private static boolean nombra(DataIntegrityViolationException conflicto, String restriccion) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(restriccion);
    }

    private static void exigirAdmin(UsuarioAutenticado admin) {
        if (admin == null || !ROL_ADMIN.equals(admin.rol())) {
            throw new AccesoDenegadoException();
        }
    }
}
