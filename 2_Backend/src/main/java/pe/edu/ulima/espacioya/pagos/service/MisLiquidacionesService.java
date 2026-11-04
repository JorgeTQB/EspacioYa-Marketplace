package pe.edu.ulima.espacioya.pagos.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.dto.MiLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.PaginaLiquidacionesDTO;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;

/**
 * "Mis liquidaciones" del arrendador (HU-19 CA2, CA3) y la consulta que HU-22 conectará a su puerto (D8). El
 * arrendador sale siempre de la sesión, nunca de la URL ni de un parámetro (R6).
 */
@Service
public class MisLiquidacionesService {

    public static final int TAMANO_PAGINA = 20;
    public static final String MENSAJE_PAGINA = "La página no es válida.";
    public static final String CAMPO_PAGINA = "pagina";

    private static final String ROL_ARRENDADOR = "ARRENDADOR";
    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");

    private final LiquidacionRepository liquidaciones;
    private final VistaLiquidaciones vista;

    public MisLiquidacionesService(LiquidacionRepository liquidaciones, VistaLiquidaciones vista) {
        this.liquidaciones = liquidaciones;
        this.vista = vista;
    }

    /** De la más reciente a la más antigua, de 20 en 20. Otro rol: 403 antes de validar {@code pagina} (R5). */
    @Transactional(readOnly = true)
    public PaginaLiquidacionesDTO<MiLiquidacionDTO> listar(UsuarioAutenticado arrendador, String pagina) {
        if (arrendador == null || !ROL_ARRENDADOR.equals(arrendador.rol())) {
            throw new AccesoDenegadoException();
        }
        var numero = numeroDePagina(pagina);
        if (numero == 0) {
            throw new ValidacionException(List.of(new ErrorCampoDTO(CAMPO_PAGINA, MENSAJE_PAGINA)));
        }
        var slice = liquidaciones.findByArrendadorId(arrendador.usuarioId(),
                PageRequest.of(numero - 1, TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "generadaEn", "id")));
        return new PaginaLiquidacionesDTO<>(numero, vista.misLiquidaciones(slice.getContent()), slice.hasNext());
    }

    /**
     * D8, S13: {@code true} si el arrendador tiene alguna liquidación {@code GENERADA} o {@code PENDIENTE_CUENTA}. Sin
     * sesión ni rol: lo conectará HU-22 a {@code cuentas.ParticipanteDatosPersonales}.
     */
    @Transactional(readOnly = true)
    public boolean tieneLiquidacionesPendientes(Long arrendadorId) {
        return liquidaciones.existsByArrendadorIdAndEstadoIn(arrendadorId, EstadoLiquidacion.POR_PAGAR);
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
}
