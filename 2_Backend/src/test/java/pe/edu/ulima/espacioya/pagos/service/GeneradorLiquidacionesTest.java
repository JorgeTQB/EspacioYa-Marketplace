package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoLiquidacion;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Liquidacion;
import pe.edu.ulima.espacioya.pagos.domain.LiquidacionDetalle;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

@ExtendWith(MockitoExtension.class)
class GeneradorLiquidacionesTest {

    // 2026-11-06 22:00 en UTC = 17:00 en Lima.
    private static final Instant CORTE = Instant.parse("2026-11-06T22:00:00Z");
    private static final Instant FIN_LIQUIDABLE = CORTE.minus(Duration.ofHours(48));
    private static final LocalDate FECHA = LocalDate.parse("2026-11-04");
    private static final long ADMIN = 1L;
    private static final String IP = "127.0.0.1";
    private static final long ANA = 7L;
    private static final long BETO = 8L;

    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private LiquidacionDetalleRepository detalles;
    @Mock
    private PagoRepository pagos;
    @Mock
    private CuentaBancariaRepository cuentas;
    @Mock
    private ReservasLiquidables reservas;
    @Mock
    private CuentaService cuentasUsuario;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private Clock reloj;

    private final AtomicLong siguienteId = new AtomicLong(100);
    private final List<LiquidacionDetalle> lineasGuardadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        lenient().when(reloj.instant()).thenReturn(CORTE);
        lenient().when(liquidaciones.saveAndFlush(any())).thenAnswer(invocacion -> {
            Liquidacion liquidacion = invocacion.getArgument(0);
            ReflectionTestUtils.setField(liquidacion, "id", siguienteId.getAndIncrement());
            return liquidacion;
        });
        lenient().when(detalles.saveAllAndFlush(any())).thenAnswer(invocacion -> {
            Iterable<LiquidacionDetalle> lineas = invocacion.getArgument(0);
            lineas.forEach(lineasGuardadas::add);
            return List.copyOf(lineasGuardadas);
        });
        lenient().when(cuentasUsuario.arrendadoresNoActivos()).thenReturn(Set.of());
        lenient().when(cuentasUsuario.arrendadoresSinIdentidadVerificada(any())).thenReturn(Set.of());
    }

    private GeneradorLiquidaciones generador(String pasarela) {
        return new GeneradorLiquidaciones(liquidaciones, detalles, pagos, cuentas, reservas, cuentasUsuario,
                auditoria, reloj, pasarela);
    }

    private GeneradorLiquidaciones generador() {
        return generador("simulada");
    }

    private static Pago pago(long id, long reservaId, String monto, String transaccion) {
        var pago = Pago.de(reservaId, transaccion, new BigDecimal(monto), EstadoPago.PAGADO, "4242",
                CORTE.minus(Duration.ofDays(10)));
        ReflectionTestUtils.setField(pago, "id", id);
        return pago;
    }

    private static Pago pago(long id, long reservaId) {
        return pago(id, reservaId, "241.50", "MP-" + id);
    }

    private static ReservaLiquidable reserva(long id, long arrendador, Instant fin, boolean admitida,
                                             boolean cancelada) {
        return new ReservaLiquidable(id, arrendador, 40L + arrendador, "Cancha " + arrendador, FECHA, 14, 17, fin,
                admitida, cancelada);
    }

    private static ReservaLiquidable usada(long id, long arrendador) {
        return reserva(id, arrendador, FIN_LIQUIDABLE, true, false);
    }

    private static CuentaBancaria cuenta(long arrendador) {
        return CuentaBancaria.registrar(arrendador, "Banco Andino", "00112233445566771234", "Ana SAC",
                CORTE.minus(Duration.ofDays(30)));
    }

    private void dados(List<Pago> pendientes, List<ReservaLiquidable> descritas) {
        when(pagos.pagadosSinLiquidar(true)).thenReturn(pendientes);
        when(reservas.describir(any())).thenReturn(descritas);
    }

    private List<EventoAuditoria> auditados() {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, atLeastOnce()).registrar(eventos.capture());
        return eventos.getAllValues();
    }

    @Test
    @DisplayName("HU-19 R22: toma el bloqueo de generación antes de leer el reloj y de buscar los pagos")
    void bloqueaAntesDelCorte() {
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of());

        var generacion = generador().generar(ADMIN, IP);

        var orden = inOrder(liquidaciones, reloj, pagos);
        orden.verify(liquidaciones).bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION);
        orden.verify(reloj).instant();
        orden.verify(pagos).pagadosSinLiquidar(true);
        assertThat(generacion.corte()).isEqualTo(CORTE);
    }

    @Test
    @DisplayName("HU-19 R7: crea una liquidación por arrendador con todas sus reservas liquidables, por id")
    void unaPorArrendador() {
        dados(List.of(pago(11, 1), pago(12, 2), pago(13, 3)),
                List.of(usada(1, BETO), usada(2, ANA), usada(3, BETO)));
        when(cuentas.findByArrendadorId(anyLong())).thenReturn(Optional.empty());

        var creadas = generador().generar(ADMIN, IP).creadas();

        assertThat(creadas).extracting(Liquidacion::getArrendadorId).containsExactly(ANA, BETO);
        assertThat(lineasGuardadas).extracting(LiquidacionDetalle::getLiquidacionId, LiquidacionDetalle::getReservaId,
                        LiquidacionDetalle::getPagoId)
                .containsExactly(tuple(creadas.get(0).getId(), 2L, 12L),
                        tuple(creadas.get(1).getId(), 1L, 11L),
                        tuple(creadas.get(1).getId(), 3L, 13L));
        verify(reservas).describir(List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("HU-19 R8: incluye la reserva cuyo fin + 48 h es igual al corte y excluye la de un segundo después")
    void bordeDe48Horas() {
        dados(List.of(pago(11, 1), pago(12, 2)), List.of(reserva(1, ANA, FIN_LIQUIDABLE, true, false),
                reserva(2, ANA, FIN_LIQUIDABLE.plusSeconds(1), true, false)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(cuenta(ANA)));

        var creadas = generador().generar(ADMIN, IP).creadas();

        assertThat(creadas).hasSize(1);
        assertThat(lineasGuardadas).extracting(LiquidacionDetalle::getReservaId).containsExactly(1L);
    }

    @Test
    @DisplayName("HU-19 R9, R12, R20: con la pasarela simulada (sin distinguir mayúsculas) pide los PAGADO sin "
            + "reembolso ni liquidar, incluidos los sim-")
    void conLaSimuladaIncluyeSimulados() {
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of());

        generador("SIMULADA").generar(ADMIN, IP);
        generador(" simulada ").generar(ADMIN, IP);

        verify(pagos, times(2)).pagadosSinLiquidar(true);
        verify(pagos, never()).pagadosSinLiquidar(false);
    }

    @Test
    @DisplayName("HU-19 R21: con otra pasarela pide los pagos sin los sim-")
    void sinLaSimuladaExcluyeSimulados() {
        when(pagos.pagadosSinLiquidar(false)).thenReturn(List.of());

        generador("mercadopago").generar(ADMIN, IP);

        verify(pagos).pagadosSinLiquidar(false);
        verify(pagos, never()).pagadosSinLiquidar(true);
    }

    @Test
    @DisplayName("HU-19 R20: una línea sim- sale simulada y marca simulada la liquidación que la contiene")
    void marcaLasSimuladas() {
        dados(List.of(pago(11, 1, "241.50", "sim-1-APROBADO-1"), pago(12, 2), pago(13, 3)),
                List.of(usada(1, ANA), usada(2, ANA), usada(3, BETO)));
        when(cuentas.findByArrendadorId(anyLong())).thenReturn(Optional.empty());

        var creadas = generador().generar(ADMIN, IP).creadas();

        assertThat(creadas).extracting(Liquidacion::isSimulada).containsExactly(true, false);
        assertThat(lineasGuardadas).extracting(LiquidacionDetalle::isSimulado).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("HU-19 R10: no incluye las reservas cuyo estado no admite liquidación ni las que el puerto no "
            + "describe")
    void excluyeNoAdmitidas() {
        dados(List.of(pago(11, 1), pago(12, 2), pago(13, 3)),
                List.of(reserva(1, ANA, FIN_LIQUIDABLE, false, false), reserva(2, ANA, FIN_LIQUIDABLE, false, true)));

        var generacion = generador().generar(ADMIN, IP);

        assertThat(generacion.creadas()).isEmpty();
        verify(liquidaciones, never()).saveAndFlush(any());
        verifyNoInteractions(detalles, auditoria, cuentas);
    }

    @Test
    @DisplayName("HU-19 R11: incluye una cancelada por el usuario sin reembolso que el puerto admite")
    void incluyeCanceladaSinReembolso() {
        dados(List.of(pago(11, 1)), List.of(reserva(1, ANA, FIN_LIQUIDABLE, true, true)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(cuenta(ANA)));

        assertThat(generador().generar(ADMIN, IP).creadas()).hasSize(1);
        assertThat(lineasGuardadas).extracting(LiquidacionDetalle::getReservaId).containsExactly(1L);
    }

    @Test
    @DisplayName("HU-19 R13: cada línea vale su pago.monto; bruto = suma, comisión = 10 % HALF_UP y neto = resto")
    void calculaLosMontos() {
        dados(List.of(pago(11, 1, "241.50", "MP-1"), pago(12, 2, "80.55", "MP-2")),
                List.of(usada(1, ANA), usada(2, ANA)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(cuenta(ANA)));

        var liquidacion = generador().generar(ADMIN, IP).creadas().getFirst();

        assertThat(lineasGuardadas).extracting(LiquidacionDetalle::getMonto)
                .containsExactly(new BigDecimal("241.50"), new BigDecimal("80.55"));
        assertThat(liquidacion.getMontoBruto()).isEqualTo(new BigDecimal("322.05"));
        assertThat(liquidacion.getComision()).isEqualTo(new BigDecimal("32.21"));
        assertThat(liquidacion.getMontoNeto()).isEqualTo(new BigDecimal("289.84"));
        assertThat(liquidacion.getGeneradaEn()).isEqualTo(CORTE);
        assertThat(liquidacion.getGeneradaPor()).isEqualTo(ADMIN);
        assertThat(liquidacion.getPeriodo()).isEqualTo(LocalDate.parse("2026-11-06"));
    }

    @Test
    @DisplayName("HU-19 R15: un arrendador con cuenta vigente, aunque esté retenida, recibe una liquidación GENERADA")
    void conCuentaRetenidaGenerada() {
        var retenida = cuenta(ANA);
        retenida.cambiar("Otro Banco", "99887766554433221100", "Ana SAC", CORTE.minus(Duration.ofHours(1)));
        dados(List.of(pago(11, 1)), List.of(usada(1, ANA)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(retenida));

        assertThat(generador().generar(ADMIN, IP).creadas()).extracting(Liquidacion::getEstado)
                .containsExactly(EstadoLiquidacion.GENERADA);
    }

    @Test
    @DisplayName("HU-19 R16: un arrendador sin cuenta recibe una liquidación PENDIENTE_CUENTA")
    void sinCuentaPendiente() {
        dados(List.of(pago(11, 1), pago(12, 2)), List.of(usada(1, ANA), usada(2, BETO)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.empty());
        when(cuentas.findByArrendadorId(BETO)).thenReturn(Optional.of(cuenta(BETO)));

        assertThat(generador().generar(ADMIN, IP).creadas()).extracting(Liquidacion::getEstado)
                .containsExactly(EstadoLiquidacion.PENDIENTE_CUENTA, EstadoLiquidacion.GENERADA);
    }

    @Test
    @DisplayName("HU-19 R17: solo inserta liquidaciones nuevas; nunca lee ni modifica las existentes")
    void soloCreaNuevas() {
        dados(List.of(pago(11, 1)), List.of(usada(1, ANA)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.empty());

        generador().generar(ADMIN, IP);

        var guardadas = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).saveAndFlush(guardadas.capture());
        assertThat(guardadas.getAllValues()).hasSize(1);
        verify(liquidaciones, never()).findParaActualizar(any());
        verify(liquidaciones, never()).findById(any());
        verify(liquidaciones, never()).findByArrendadorId(any(), any());
    }

    @Test
    @DisplayName("HU-19 R18: sin pagos pendientes no consulta reservas, no crea nada y no audita")
    void sinPendientes() {
        when(pagos.pagadosSinLiquidar(true)).thenReturn(List.of());

        var generacion = generador().generar(ADMIN, IP);

        assertThat(generacion.creadas()).isEmpty();
        assertThat(generacion.corte()).isEqualTo(CORTE);
        verifyNoInteractions(reservas, detalles, auditoria, cuentas, cuentasUsuario);
        verify(liquidaciones, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-19 R18: con pagos pendientes pero ninguna reserva madura, no crea nada")
    void ningunaMadura() {
        dados(List.of(pago(11, 1)), List.of(reserva(1, ANA, CORTE, true, false)));

        assertThat(generador().generar(ADMIN, IP).creadas()).isEmpty();
        verifyNoInteractions(detalles, auditoria, cuentas);
    }

    @Test
    @DisplayName("HU-19 R24, R45: un arrendador suspendido y sin identidad verificada se liquida igual y la auditoría "
            + "lleva sus avisos")
    void avisosNoFiltran() {
        dados(List.of(pago(11, 1), pago(12, 2)), List.of(usada(1, ANA), usada(2, BETO)));
        when(cuentas.findByArrendadorId(anyLong())).thenReturn(Optional.empty());
        when(cuentasUsuario.arrendadoresNoActivos()).thenReturn(Set.of(ANA, 99L));
        when(cuentasUsuario.arrendadoresSinIdentidadVerificada(Set.of(ANA, BETO))).thenReturn(Set.of(ANA));

        var creadas = generador().generar(ADMIN, IP).creadas();

        assertThat(creadas).extracting(Liquidacion::getArrendadorId).containsExactly(ANA, BETO);
        assertThat(auditados()).extracting(EventoAuditoria::detalle).containsExactly(
                "arrendadorId=7;reservas=1;bruto=241.50;comision=24.15;neto=217.35;estado=PENDIENTE_CUENTA;"
                        + "periodo=2026-11-06;simulada=false;avisos=CUENTA_NO_ACTIVA,IDENTIDAD_NO_VERIFICADA",
                "arrendadorId=8;reservas=1;bruto=241.50;comision=24.15;neto=217.35;estado=PENDIENTE_CUENTA;"
                        + "periodo=2026-11-06;simulada=false");
    }

    @Test
    @DisplayName("HU-19 R45: audita LIQUIDACION_GENERADA sobre la liquidación con el admin, la IP y el detalle")
    void auditaLaGeneracion() {
        dados(List.of(pago(11, 1, "241.50", "sim-1-APROBADO-1"), pago(12, 2)), List.of(usada(1, ANA), usada(2, ANA)));
        when(cuentas.findByArrendadorId(ANA)).thenReturn(Optional.of(cuenta(ANA)));

        var liquidacion = generador().generar(ADMIN, IP).creadas().getFirst();

        assertThat(auditados()).singleElement().isEqualTo(new EventoAuditoria(ADMIN, "LIQUIDACION_GENERADA",
                "liquidacion", liquidacion.getId(), IP, "arrendadorId=7;reservas=2;bruto=483.00;comision=48.30;"
                + "neto=434.70;estado=GENERADA;periodo=2026-11-06;simulada=true"));
    }
}
