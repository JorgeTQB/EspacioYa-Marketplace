package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;
import pe.edu.ulima.espacioya.pagos.dto.LineaLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.MiLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.PagoLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

@ExtendWith(MockitoExtension.class)
class MisLiquidacionesServiceTest {

    private static final Instant GENERADA_EN = Instant.parse("2026-11-06T22:00:00Z");
    private static final Instant PAGADA_EN = GENERADA_EN.plus(Duration.ofDays(1));
    private static final LocalDate FECHA = LocalDate.parse("2026-11-04");
    private static final UsuarioAutenticado ANA = new UsuarioAutenticado(7L, 31L, "ARRENDADOR");
    private static final Sort RECIENTES_PRIMERO = Sort.by(Sort.Direction.DESC, "generadaEn", "id");

    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private LiquidacionDetalleRepository detalles;
    @Mock
    private ReservasLiquidables reservas;
    @Mock
    private CuentaService cuentasUsuario;

    private MisLiquidacionesService servicio;

    @BeforeEach
    void preparar() {
        servicio = new MisLiquidacionesService(liquidaciones, new VistaLiquidaciones(detalles, reservas,
                cuentasUsuario));
    }

    private static Liquidacion liquidacion(long id, boolean conCuenta) {
        var liquidacion = Liquidacion.generar(7L, List.of(new BigDecimal("241.50")), conCuenta, true, GENERADA_EN, 1L);
        ReflectionTestUtils.setField(liquidacion, "id", id);
        return liquidacion;
    }

    private static Liquidacion pagada(long id) {
        var liquidacion = liquidacion(id, true);
        var cuenta = CuentaBancaria.registrar(7L, "Banco Andino", "00112233445566771234", "Ana SAC",
                GENERADA_EN.minus(Duration.ofDays(30)));
        liquidacion.marcarPagada("OP-00123456", cuenta, 1L, PAGADA_EN);
        return liquidacion;
    }

    @Test
    @DisplayName("HU-19 R5: otro rol o sin sesión recibe 403 antes de validar la página y sin leer liquidaciones")
    void soloElArrendador() {
        for (var otro : Arrays.asList(new UsuarioAutenticado(9L, 1L, "USUARIO"),
                new UsuarioAutenticado(1L, 2L, "ADMIN"), null)) {
            assertThatThrownBy(() -> servicio.listar(otro, "abc")).isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(liquidaciones, detalles, reservas, cuentasUsuario);
    }

    @Test
    @DisplayName("HU-19 R6, R42: consulta solo las del arrendador de la sesión, de la más reciente a la más antigua, "
            + "de 20 en 20 con hayMas")
    void soloLasSuyas() {
        var pedida = PageRequest.of(1, 20, RECIENTES_PRIMERO);
        when(liquidaciones.findByArrendadorId(7L, pedida))
                .thenReturn(new SliceImpl<>(List.of(liquidacion(100L, true)), pedida, true));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(100L))).thenReturn(List.of());

        var pagina = servicio.listar(ANA, "2");

        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.hayMas()).isTrue();
        assertThat(pagina.liquidaciones()).extracting(MiLiquidacionDTO::id).containsExactly(100L);
        verify(liquidaciones).findByArrendadorId(7L, pedida);
    }

    @Test
    @DisplayName("HU-19 R42: cada liquidación trae periodo, estado, montos, simulada, líneas y, si está PAGADA, la "
            + "operación, la fecha y el destino enmascarado")
    void contenido() {
        var pedida = PageRequest.of(0, 20, RECIENTES_PRIMERO);
        when(liquidaciones.findByArrendadorId(7L, pedida))
                .thenReturn(new SliceImpl<>(List.of(pagada(101L), liquidacion(100L, false)), pedida, false));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(101L, 100L))).thenReturn(List.of(
                LiquidacionDetalle.de(100L, 2L, 12L, new BigDecimal("241.50"), true),
                LiquidacionDetalle.de(101L, 1L, 11L, new BigDecimal("241.50"), true)));
        when(reservas.describir(any())).thenReturn(List.of(
                new ReservaLiquidable(1L, 7L, 40L, "Cancha Los Olivos", FECHA, 14, 17, GENERADA_EN, true, false),
                new ReservaLiquidable(2L, 7L, 40L, "Cancha Los Olivos", FECHA, 8, 9, GENERADA_EN, true, true)));

        var pagina = servicio.listar(ANA, null);

        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.hayMas()).isFalse();
        assertThat(pagina.liquidaciones()).containsExactly(
                new MiLiquidacionDTO(101L, LocalDate.parse("2026-11-06"), GENERADA_EN, "PAGADA",
                        new BigDecimal("241.50"), new BigDecimal("24.15"), new BigDecimal("217.35"), true,
                        new PagoLiquidacionDTO("OP-00123456", PAGADA_EN, "Banco Andino", "****1234"),
                        List.of(new LineaLiquidacionDTO(1L, "Cancha Los Olivos", FECHA, 14, 17,
                                new BigDecimal("241.50"), true, false))),
                new MiLiquidacionDTO(100L, LocalDate.parse("2026-11-06"), GENERADA_EN, "PENDIENTE_CUENTA",
                        new BigDecimal("241.50"), new BigDecimal("24.15"), new BigDecimal("217.35"), true, null,
                        List.of(new LineaLiquidacionDTO(2L, "Cancha Los Olivos", FECHA, 8, 9,
                                new BigDecimal("241.50"), true, true))));
        verify(reservas).describir(List.of(2L, 1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "", "2147483648"})
    @DisplayName("HU-19 R43: una página que no es un entero de 1 en adelante da 400 en pagina sin consultar")
    void paginaInvalida(String pagina) {
        assertThatThrownBy(() -> servicio.listar(ANA, pagina)).isInstanceOf(ValidacionException.class)
                .satisfies(e -> assertThat(((ValidacionException) e).getErrores())
                        .containsExactly(new ErrorCampoDTO("pagina", "La página no es válida.")));
        verifyNoInteractions(liquidaciones);
    }

    @Test
    @DisplayName("HU-19 R44: la respuesta no trae datos del usuario que reservó, del pago ni del admin")
    void sinDatosAjenos() {
        var pedida = PageRequest.of(0, 20, RECIENTES_PRIMERO);
        when(liquidaciones.findByArrendadorId(7L, pedida))
                .thenReturn(new SliceImpl<>(List.of(pagada(101L)), pedida, false));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(101L))).thenReturn(List.of());

        servicio.listar(ANA, null);

        var campos = Stream.of(MiLiquidacionDTO.class, LineaLiquidacionDTO.class, PagoLiquidacionDTO.class)
                .flatMap(clase -> Arrays.stream(clase.getRecordComponents()))
                .map(componente -> componente.getName().toLowerCase()).toList();
        assertThat(campos).noneMatch(nombre -> nombre.contains("usuario") || nombre.contains("correo")
                || nombre.contains("telefono") || nombre.contains("transaccion") || nombre.contains("ultimos")
                || nombre.contains("pagoid") || nombre.contains("generadapor") || nombre.contains("pagadapor")
                || nombre.contains("admin"));
        verifyNoInteractions(cuentasUsuario);
    }

    @Test
    @DisplayName("HU-19 R47: tieneLiquidacionesPendientes pregunta por GENERADA o PENDIENTE_CUENTA del arrendador")
    void pendientesParaHu22() {
        when(liquidaciones.existsByArrendadorIdAndEstadoIn(7L, EstadoLiquidacion.POR_PAGAR)).thenReturn(true);
        when(liquidaciones.existsByArrendadorIdAndEstadoIn(8L, EstadoLiquidacion.POR_PAGAR)).thenReturn(false);

        assertThat(servicio.tieneLiquidacionesPendientes(7L)).isTrue();
        assertThat(servicio.tieneLiquidacionesPendientes(8L)).isFalse();
        assertThat(EstadoLiquidacion.POR_PAGAR)
                .containsExactlyInAnyOrder(EstadoLiquidacion.GENERADA, EstadoLiquidacion.PENDIENTE_CUENTA);
    }
}
