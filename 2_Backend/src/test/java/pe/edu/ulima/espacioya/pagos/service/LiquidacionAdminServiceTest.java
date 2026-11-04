package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;
import pe.edu.ulima.espacioya.pagos.dto.CuentaDestinoDTO;
import pe.edu.ulima.espacioya.pagos.dto.LineaLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.LiquidacionAdminDTO;
import pe.edu.ulima.espacioya.pagos.dto.MarcarLiquidacionPagadaDTO;
import pe.edu.ulima.espacioya.pagos.dto.PagoLiquidacionDTO;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

@ExtendWith(MockitoExtension.class)
class LiquidacionAdminServiceTest {

    private static final Instant GENERADA_EN = Instant.parse("2026-11-06T22:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-11-07T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.parse("2026-11-04");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 91L, "ADMIN");
    private static final String IP = "127.0.0.1";
    private static final long ANA = 7L;
    private static final long BETO = 8L;
    private static final String NUMERO = "00112233445566771234";
    private static final MarcarLiquidacionPagadaDTO OPERACION = new MarcarLiquidacionPagadaDTO("OP-00123456");

    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private CuentaBancariaRepository cuentas;
    @Mock
    private GeneradorLiquidaciones generador;
    @Mock
    private LiquidacionDetalleRepository detalles;
    @Mock
    private ReservasLiquidables reservas;
    @Mock
    private CuentaService cuentasUsuario;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private Clock reloj;
    @Mock
    private NotificadorCorreo notificador;

    private LiquidacionAdminService servicio;

    @BeforeEach
    void preparar() {
        lenient().when(reloj.instant()).thenReturn(AHORA);
        lenient().when(cuentasUsuario.arrendadoresNoActivos()).thenReturn(Set.of());
        lenient().when(cuentasUsuario.arrendadoresSinIdentidadVerificada(any())).thenReturn(Set.of());
        lenient().when(cuentasUsuario.nombres(any())).thenReturn(Map.of(ANA, "Ana Reservada"));
        servicio = new LiquidacionAdminService(liquidaciones, cuentas, generador,
                new VistaLiquidaciones(detalles, reservas, cuentasUsuario), auditoria, reloj);
    }

    private static Liquidacion liquidacion(long id, long arrendador, boolean conCuenta, boolean simulada) {
        var liquidacion = Liquidacion.generar(arrendador, List.of(new BigDecimal("241.50"), new BigDecimal("80.50")),
                conCuenta, simulada, GENERADA_EN, ADMIN.usuarioId());
        ReflectionTestUtils.setField(liquidacion, "id", id);
        return liquidacion;
    }

    private static CuentaBancaria cuenta() {
        var cuenta = CuentaBancaria.registrar(ANA, "Banco Andino", NUMERO, "Inversiones Ana SAC",
                AHORA.minus(Duration.ofDays(30)));
        ReflectionTestUtils.setField(cuenta, "id", 55L);
        return cuenta;
    }

    private static CuentaBancaria retenida() {
        var cuenta = cuenta();
        cuenta.cambiar("Otro Banco", "99887766554433229999", "Inversiones Ana SAC", AHORA.minus(Duration.ofHours(5)));
        return cuenta;
    }

    private static Liquidacion pagada(long id) {
        var liquidacion = liquidacion(id, ANA, true, true);
        liquidacion.marcarPagada("PRIMERA-1", cuenta(), 2L, AHORA.minus(Duration.ofHours(1)));
        return liquidacion;
    }

    private void lineasDe(long liquidacionId) {
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(liquidacionId)))
                .thenReturn(List.of(LiquidacionDetalle.de(liquidacionId, 1L, 11L, new BigDecimal("241.50"), true),
                        LiquidacionDetalle.de(liquidacionId, 2L, 12L, new BigDecimal("80.50"), false)));
        lenient().when(reservas.describir(List.of(1L, 2L))).thenReturn(List.of(
                new ReservaLiquidable(1L, ANA, 40L, "Cancha Los Olivos", FECHA, 14, 17, GENERADA_EN, true, false),
                new ReservaLiquidable(2L, ANA, 40L, "Cancha Los Olivos", FECHA, 22, 24, GENERADA_EN, true, true)));
    }

    private List<EventoAuditoria> auditados() {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, org.mockito.Mockito.atLeastOnce()).registrar(eventos.capture());
        return eventos.getAllValues();
    }

    // ── Acceso ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R2: un rol distinto de ADMIN o sin sesión recibe 403 en las cuatro operaciones sin leer nada")
    void soloElAdmin() {
        for (var otro : Arrays.asList(new UsuarioAutenticado(7L, 1L, "ARRENDADOR"),
                new UsuarioAutenticado(9L, 2L, "USUARIO"), null)) {
            assertThatThrownBy(() -> servicio.generar(otro, IP)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.listar(otro, "nada", "x")).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.detalle(otro, "1", IP)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.marcarPagada(otro, "1", OPERACION, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(liquidaciones, cuentas, generador, detalles, reservas, cuentasUsuario, auditoria);
    }

    // ── Generar ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R14: la generación solo recibe el admin y la IP de la conexión; nada del cliente")
    void generarSoloConDatosDelServidor() {
        when(generador.generar(ADMIN.usuarioId(), IP)).thenReturn(new GeneradorLiquidaciones.Generacion(AHORA,
                List.of()));

        servicio.generar(ADMIN, IP);

        verify(generador).generar(ADMIN.usuarioId(), IP);
        assertThat(LiquidacionAdminService.class.getMethods()).filteredOn(m -> m.getName().equals("generar"))
                .singleElement().satisfies(m -> assertThat(m.getParameterTypes())
                        .containsExactly(UsuarioAutenticado.class, String.class));
    }

    @Test
    @DisplayName("HU-19 R18: sin nada que liquidar responde el corte y la lista vacía")
    void generarSinNada() {
        when(generador.generar(ADMIN.usuarioId(), IP)).thenReturn(new GeneradorLiquidaciones.Generacion(AHORA,
                List.of()));

        var respuesta = servicio.generar(ADMIN, IP);

        assertThat(respuesta.corte()).isEqualTo(AHORA);
        assertThat(respuesta.liquidaciones()).isEmpty();
        verifyNoInteractions(detalles, cuentasUsuario);
    }

    @Test
    @DisplayName("HU-19 R19, R37: la respuesta trae por liquidación id, arrendador, periodo, estado, montos, reservas, "
            + "simulada y avisos; sin nombre usa Arrendador #id")
    void generarRespondeLoCreado() {
        var deAna = liquidacion(100L, ANA, true, true);
        var deBeto = liquidacion(101L, BETO, false, false);
        when(generador.generar(ADMIN.usuarioId(), IP)).thenReturn(new GeneradorLiquidaciones.Generacion(AHORA,
                List.of(deAna, deBeto)));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(100L, 101L))).thenReturn(List.of(
                LiquidacionDetalle.de(100L, 1L, 11L, new BigDecimal("241.50"), true),
                LiquidacionDetalle.de(100L, 2L, 12L, new BigDecimal("80.50"), false),
                LiquidacionDetalle.de(101L, 3L, 13L, new BigDecimal("322.00"), false)));
        when(cuentasUsuario.arrendadoresNoActivos()).thenReturn(Set.of(BETO));
        when(cuentasUsuario.arrendadoresSinIdentidadVerificada(Set.of(ANA, BETO))).thenReturn(Set.of(BETO));

        var respuesta = servicio.generar(ADMIN, IP);

        assertThat(respuesta.corte()).isEqualTo(AHORA);
        assertThat(respuesta.liquidaciones()).containsExactly(
                new LiquidacionAdminDTO(100L, ANA, "Ana Reservada", LocalDate.parse("2026-11-06"), GENERADA_EN,
                        "GENERADA", new BigDecimal("322.00"), new BigDecimal("32.20"), new BigDecimal("289.80"), 2,
                        true, List.of(), null, null),
                new LiquidacionAdminDTO(101L, BETO, "Arrendador #8", LocalDate.parse("2026-11-06"), GENERADA_EN,
                        "PENDIENTE_CUENTA", new BigDecimal("322.00"), new BigDecimal("32.20"),
                        new BigDecimal("289.80"), 1, false, List.of("CUENTA_NO_ACTIVA", "IDENTIDAD_NO_VERIFICADA"),
                        null, null));
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("HU-19 R22: si uq_liquidacion_detalle_reserva salta pese al bloqueo responde 400 de generación "
            + "cruzada; otra violación se relanza")
    void traduceLaGeneracionCruzada() {
        var cruzada = new DataIntegrityViolationException("x", new SQLException(
                "duplicate key value violates unique constraint \"uq_liquidacion_detalle_reserva\""));
        var otra = new DataIntegrityViolationException("x", new SQLException("violates check constraint ck_otro"));
        when(generador.generar(ADMIN.usuarioId(), IP)).thenThrow(cruzada).thenThrow(otra);

        assertThatThrownBy(() -> servicio.generar(ADMIN, IP)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(LiquidacionAdminService.MENSAJE_GENERACION_CRUZADA)
                .hasMessage("Otra generación de liquidaciones se cruzó con esta. Vuelve a cargar la lista.");
        assertThatThrownBy(() -> servicio.generar(ADMIN, IP)).isSameAs(otra);
    }

    // ── Listar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R33: sin grupo o con por-pagar lista GENERADA y PENDIENTE_CUENTA por generada_en e id "
            + "ascendentes, de 20 en 20 y con hayMas")
    void listarPorPagar() {
        var pedida = PageRequest.of(1, 20, Sort.by("generadaEn", "id"));
        var deAna = liquidacion(100L, ANA, true, false);
        when(liquidaciones.findByEstadoIn(EstadoLiquidacion.POR_PAGAR, pedida))
                .thenReturn(new SliceImpl<>(List.of(deAna), pedida, true));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(100L))).thenReturn(List.of());

        var sinGrupo = servicio.listar(ADMIN, null, "2");
        var porPagar = servicio.listar(ADMIN, "por-pagar", "2");

        for (var pagina : List.of(sinGrupo, porPagar)) {
            assertThat(pagina.pagina()).isEqualTo(2);
            assertThat(pagina.hayMas()).isTrue();
            assertThat(pagina.liquidaciones()).extracting(LiquidacionAdminDTO::id).containsExactly(100L);
        }
    }

    @Test
    @DisplayName("HU-19 R33: sin página pide la 1")
    void listarPrimeraPagina() {
        var pedida = PageRequest.of(0, 20, Sort.by("generadaEn", "id"));
        when(liquidaciones.findByEstadoIn(EstadoLiquidacion.POR_PAGAR, pedida))
                .thenReturn(new SliceImpl<>(List.of(), pedida, false));

        var pagina = servicio.listar(ADMIN, null, null);

        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.liquidaciones()).isEmpty();
        assertThat(pagina.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-19 R34: pagadas lista PAGADA por pagada_en e id descendentes, con operación y fecha de pago")
    void listarPagadas() {
        var pedida = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "pagadaEn", "id"));
        when(liquidaciones.findByEstadoIn(List.of(EstadoLiquidacion.PAGADA), pedida))
                .thenReturn(new SliceImpl<>(List.of(pagada(100L)), pedida, false));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(100L))).thenReturn(List.of());

        var pagina = servicio.listar(ADMIN, "pagadas", "1");

        assertThat(pagina.liquidaciones()).singleElement().satisfies(dto -> {
            assertThat(dto.estado()).isEqualTo("PAGADA");
            assertThat(dto.numeroOperacion()).isEqualTo("PRIMERA-1");
            assertThat(dto.pagadaEn()).isEqualTo(AHORA.minus(Duration.ofHours(1)));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "", " 1", "2147483648"})
    @DisplayName("HU-19 R35: un grupo y una página inválidos dan 400 con un error por parámetro, sin consultar")
    void listarParametrosInvalidos(String pagina) {
        assertThatThrownBy(() -> servicio.listar(ADMIN, "todas", pagina)).isInstanceOf(ValidacionException.class)
                .satisfies(e -> assertThat(((ValidacionException) e).getErrores()).containsExactly(
                        new ErrorCampoDTO("grupo", "Elige por pagar o pagadas."),
                        new ErrorCampoDTO("pagina", "La página no es válida.")));
        assertThatThrownBy(() -> servicio.listar(ADMIN, "por-pagar", pagina)).isInstanceOf(ValidacionException.class)
                .satisfies(e -> assertThat(((ValidacionException) e).getErrores())
                        .containsExactly(new ErrorCampoDTO("pagina", "La página no es válida.")));
        verifyNoInteractions(liquidaciones);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PAGADAS", "por_pagar", " pagadas"})
    @DisplayName("HU-19 R35: un grupo que no es exactamente por-pagar ni pagadas da 400 en grupo")
    void listarGrupoInvalido(String grupo) {
        assertThatThrownBy(() -> servicio.listar(ADMIN, grupo, null)).isInstanceOf(ValidacionException.class)
                .satisfies(e -> assertThat(((ValidacionException) e).getErrores())
                        .containsExactly(new ErrorCampoDTO("grupo", "Elige por pagar o pagadas.")));
        verifyNoInteractions(liquidaciones);
    }

    @Test
    @DisplayName("HU-19 R37, R40: la lista trae los avisos de la consulta y ningún número de cuenta")
    void listarConAvisos() {
        var pedida = PageRequest.of(0, 20, Sort.by("generadaEn", "id"));
        when(liquidaciones.findByEstadoIn(EstadoLiquidacion.POR_PAGAR, pedida))
                .thenReturn(new SliceImpl<>(List.of(liquidacion(100L, ANA, true, false)), pedida, false));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(List.of(100L))).thenReturn(List.of());
        when(cuentasUsuario.arrendadoresSinIdentidadVerificada(Set.of(ANA))).thenReturn(Set.of(ANA));

        var dto = servicio.listar(ADMIN, null, null).liquidaciones().getFirst();

        assertThat(dto.avisos()).containsExactly("IDENTIDAD_NO_VERIFICADA");
        assertThat(dto.toString()).doesNotContain(NUMERO).doesNotContain("1234");
        verifyNoInteractions(cuentas, auditoria);
    }

    // ── Detalle ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "12345678901234567890", ""})
    @DisplayName("HU-19 R31: un id mal escrito da 404 en el detalle y al marcar, sin leer ni escribir nada")
    void idMalEscrito(String id) {
        assertThatThrownBy(() -> servicio.detalle(ADMIN, id, IP)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.marcarPagada(ADMIN, id, OPERACION, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(liquidaciones, cuentas, auditoria);
    }

    @Test
    @DisplayName("HU-19 R31: un id inexistente da 404 en el detalle y al marcar, sin escribir nada")
    void idInexistente() {
        when(liquidaciones.findById(404L)).thenReturn(Optional.empty());
        when(liquidaciones.findParaActualizar(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalle(ADMIN, "404", IP)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.marcarPagada(ADMIN, "404", OPERACION, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(cuentas, auditoria);
        verify(liquidaciones, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-19 R36, R38: el detalle pagable trae líneas, cuenta con número completo y pagable")
    void detallePagable() {
        when(liquidaciones.findById(100L)).thenReturn(Optional.of(liquidacion(100L, ANA, true, true)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(cuenta()));
        lineasDe(100L);

        var detalle = servicio.detalle(ADMIN, "100", IP);

        assertThat(detalle.id()).isEqualTo(100L);
        assertThat(detalle.arrendadorNombre()).isEqualTo("Ana Reservada");
        assertThat(detalle.estado()).isEqualTo("GENERADA");
        assertThat(detalle.periodo()).isEqualTo(LocalDate.parse("2026-11-06"));
        assertThat(detalle.montoNeto()).isEqualTo(new BigDecimal("289.80"));
        assertThat(detalle.simulada()).isTrue();
        assertThat(detalle.pagable()).isTrue();
        assertThat(detalle.pago()).isNull();
        assertThat(detalle.cuenta()).isEqualTo(new CuentaDestinoDTO("Banco Andino", "Inversiones Ana SAC", "****1234",
                NUMERO, "DISPONIBLE", null));
        assertThat(detalle.reservas()).containsExactly(
                new LineaLiquidacionDTO(1L, "Cancha Los Olivos", FECHA, 14, 17, new BigDecimal("241.50"), true, false),
                new LineaLiquidacionDTO(2L, "Cancha Los Olivos", FECHA, 22, 24, new BigDecimal("80.50"), false,
                        true));
    }

    @Test
    @DisplayName("HU-19 R39: revelar el número completo audita CUENTA_BANCARIA_CONSULTADA_ADMIN sin el número")
    void detallePagableAudita() {
        when(liquidaciones.findById(100L)).thenReturn(Optional.of(liquidacion(100L, ANA, true, true)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(cuenta()));
        lineasDe(100L);

        servicio.detalle(ADMIN, "100", IP);

        assertThat(auditados()).containsExactly(new EventoAuditoria(1L, "CUENTA_BANCARIA_CONSULTADA_ADMIN",
                "cuenta_bancaria", 55L, IP, "liquidacionId=100;arrendadorId=7;cuenta=****1234"));
    }

    @Test
    @DisplayName("HU-19 R36, R40: con la cuenta retenida el detalle no es pagable, va enmascarado con la fecha de "
            + "retención y no audita")
    void detalleRetenido() {
        var retenida = retenida();
        when(liquidaciones.findById(100L)).thenReturn(Optional.of(liquidacion(100L, ANA, true, false)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(retenida));
        lineasDe(100L);

        var detalle = servicio.detalle(ADMIN, "100", IP);

        assertThat(detalle.pagable()).isFalse();
        assertThat(detalle.cuenta()).isEqualTo(new CuentaDestinoDTO("Otro Banco", "Inversiones Ana SAC", "****9999",
                null, "RETENIDA", retenida.getRetenerHasta()));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-19 R36, R40: sin cuenta el detalle trae la cuenta nula y no es pagable; PAGADA trae a dónde se "
            + "pagó, sin número completo")
    void detalleSinCuentaYPagada() {
        when(liquidaciones.findById(100L)).thenReturn(Optional.of(liquidacion(100L, ANA, false, false)));
        when(liquidaciones.findById(101L)).thenReturn(Optional.of(pagada(101L)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.empty()).thenReturn(Optional.of(cuenta()));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(any())).thenReturn(List.of());

        var sinCuenta = servicio.detalle(ADMIN, "100", IP);
        var yaPagada = servicio.detalle(ADMIN, "101", IP);

        assertThat(sinCuenta.cuenta()).isNull();
        assertThat(sinCuenta.pagable()).isFalse();
        assertThat(sinCuenta.estado()).isEqualTo("PENDIENTE_CUENTA");
        assertThat(yaPagada.pagable()).isFalse();
        assertThat(yaPagada.cuenta().numeroCompleto()).isNull();
        assertThat(yaPagada.pago()).isEqualTo(new PagoLiquidacionDTO("PRIMERA-1", AHORA.minus(Duration.ofHours(1)),
                "Banco Andino", "****1234"));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-19 R40: CuentaDestinoDTO.toString no muestra el número completo")
    void toStringSinNumero() {
        var dto = new CuentaDestinoDTO("Banco Andino", "Ana SAC", "****1234", NUMERO, "DISPONIBLE", null);

        assertThat(dto.toString()).doesNotContain(NUMERO).contains("****1234");
    }

    // ── Marcar como pagada ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R25: una GENERADA con cuenta no retenida queda PAGADA con operación, fecha, admin y destino "
            + "enmascarado, y responde sin el número completo")
    void marcarGenerada() {
        var liquidacion = liquidacion(100L, ANA, true, true);
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(cuenta()));
        lineasDe(100L);

        var detalle = servicio.marcarPagada(ADMIN, "100", OPERACION, IP);

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PAGADA);
        assertThat(liquidacion.getNumeroOperacion()).isEqualTo("OP-00123456");
        assertThat(liquidacion.getPagadaEn()).isEqualTo(AHORA);
        assertThat(liquidacion.getPagadaPor()).isEqualTo(1L);
        assertThat(liquidacion.getCuentaDestino()).isEqualTo("****1234");
        verify(liquidaciones).saveAndFlush(liquidacion);
        assertThat(detalle.estado()).isEqualTo("PAGADA");
        assertThat(detalle.pagable()).isFalse();
        assertThat(detalle.cuenta().numeroCompleto()).isNull();
        assertThat(detalle.pago()).isEqualTo(new PagoLiquidacionDTO("OP-00123456", AHORA, "Banco Andino",
                "****1234"));
    }

    @Test
    @DisplayName("HU-19 R26: una PENDIENTE_CUENTA cuyo arrendador ahora tiene cuenta pasa directo a PAGADA")
    void marcarPendienteDeCuenta() {
        var liquidacion = liquidacion(100L, ANA, false, false);
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(cuenta()));
        lineasDe(100L);

        servicio.marcarPagada(ADMIN, "100", OPERACION, IP);

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PAGADA);
        assertThat(auditados()).singleElement().satisfies(evento ->
                assertThat(evento.detalle()).startsWith("estadoAnterior=PENDIENTE_CUENTA;"));
    }

    @Test
    @DisplayName("HU-19 R25: bloquea la liquidación, luego toma ahora y luego bloquea la cuenta (DT-8)")
    void ordenDeBloqueos() {
        var liquidacion = liquidacion(100L, ANA, true, false);
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(cuenta()));
        lineasDe(100L);

        servicio.marcarPagada(ADMIN, "100", OPERACION, IP);

        var orden = inOrder(liquidaciones, reloj, cuentas);
        orden.verify(liquidaciones).findParaActualizar(100L);
        orden.verify(reloj).instant();
        orden.verify(cuentas).findParaActualizar(ANA);
        verify(cuentas, never()).findByArrendadorId(any());
    }

    @Test
    @DisplayName("HU-19 R27: sin cuenta vigente responde 400 sin cuenta y no modifica ni audita")
    void marcarSinCuenta() {
        var liquidacion = liquidacion(100L, ANA, false, false);
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.marcarPagada(ADMIN, "100", OPERACION, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El arrendador aún no registra una cuenta bancaria. Podrás marcar esta liquidación como "
                        + "pagada cuando la registre.");

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.PENDIENTE_CUENTA);
        verify(liquidaciones, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-19 R28: con la cuenta retenida responde 400 con retener_hasta en hora de Lima y no modifica")
    void marcarRetenida() {
        var liquidacion = liquidacion(100L, ANA, true, false);
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(retenida()));

        // retener_hasta = AHORA − 5 h + 72 h = 2026-11-10T10:00Z = 05:00 en Lima.
        assertThatThrownBy(() -> servicio.marcarPagada(ADMIN, "100", OPERACION, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La cuenta bancaria del arrendador cambió hace poco. Podrás marcar esta liquidación como "
                        + "pagada desde el 10/11/2026 05:00.");

        assertThat(liquidacion.getEstado()).isEqualTo(EstadoLiquidacion.GENERADA);
        verify(liquidaciones, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-19 R29, R32: una PAGADA responde 400 ya pagada y conserva operación, fecha y quién la marcó")
    void marcarYaPagada() {
        var liquidacion = pagada(100L);
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(cuenta()));

        assertThatThrownBy(() -> servicio.marcarPagada(ADMIN, "100", new MarcarLiquidacionPagadaDTO("OTRA-1"), IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("Esta liquidación ya está pagada.");

        assertThat(liquidacion.getNumeroOperacion()).isEqualTo("PRIMERA-1");
        assertThat(liquidacion.getPagadaPor()).isEqualTo(2L);
        assertThat(liquidacion.getPagadaEn()).isEqualTo(AHORA.minus(Duration.ofHours(1)));
        verify(liquidaciones, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-19 R46: audita LIQUIDACION_PAGADA con estado anterior, operación, destino enmascarado, neto y "
            + "simulada, sin el número completo")
    void marcarAudita() {
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion(100L, ANA, true, true)));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(cuenta()));
        lineasDe(100L);

        servicio.marcarPagada(ADMIN, "100", OPERACION, IP);

        assertThat(auditados()).containsExactly(new EventoAuditoria(1L, "LIQUIDACION_PAGADA", "liquidacion", 100L, IP,
                "estadoAnterior=GENERADA;numeroOperacion=OP-00123456;cuenta=Banco Andino/****1234;neto=289.80;"
                        + "simulada=true"));
    }

    @Test
    @DisplayName("HU-19 R48: ni generar ni marcar pagada envían correo")
    void sinCorreo() {
        var liquidacion = liquidacion(100L, ANA, true, false);
        when(generador.generar(ADMIN.usuarioId(), IP)).thenReturn(new GeneradorLiquidaciones.Generacion(AHORA,
                List.of(liquidacion)));
        when(detalles.findByLiquidacionIdInOrderByLiquidacionIdAscReservaIdAsc(any())).thenReturn(List.of());
        when(liquidaciones.findParaActualizar(100L)).thenReturn(Optional.of(liquidacion));
        when(cuentas.findParaActualizar(ANA)).thenReturn(Optional.of(cuenta()));

        servicio.generar(ADMIN, IP);
        servicio.marcarPagada(ADMIN, "100", OPERACION, IP);

        verifyNoInteractions(notificador);
        for (var clase : List.of(LiquidacionAdminService.class, GeneradorLiquidaciones.class,
                VistaLiquidaciones.class, MisLiquidacionesService.class)) {
            assertThat(clase.getDeclaredFields()).extracting(Field::getType).doesNotContain(NotificadorCorreo.class);
        }
    }
}
