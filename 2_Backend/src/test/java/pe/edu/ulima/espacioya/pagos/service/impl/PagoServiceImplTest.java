package pe.edu.ulima.espacioya.pagos.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.EstadoReembolso;
import pe.edu.ulima.espacioya.pagos.domain.OrigenReembolso;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.domain.Reembolso;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionDetalleRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.PagoDeReserva;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoDePago;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorReclamo;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.reembolso.Disparo;
import pe.edu.ulima.espacioya.pagos.service.reembolso.EjecutorReembolsos;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudReembolso;
import pe.edu.ulima.espacioya.pagos.service.reembolso.SolicitudesReembolso;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    private static final long RESERVA_ID = 7L;
    private static final long PAGO_ID = 31L;
    private static final long USUARIO_ID = 12L;
    private static final String IP = "10.17.0.1";
    private static final long REEMBOLSO_ID = 4L;
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");

    @Mock
    private PagoRepository pagos;
    @Mock
    private ReembolsoRepository reembolsos;
    @Mock
    private LiquidacionRepository liquidaciones;
    @Mock
    private LiquidacionDetalleRepository detalles;
    @Mock
    private SolicitudesReembolso solicitudes;
    @Mock
    private AuditoriaService auditoria;

    private final EjecutorReembolsos ejecutor = spy(new EjecutorReembolsos());
    private final SolicitudReembolso comando = mock(SolicitudReembolso.class);
    private PagoServiceImpl servicio;

    // Como dentro de la transacción de la cancelación: el Invoker registra el comando para después del commit.
    @BeforeEach
    void preparar() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        servicio = new PagoServiceImpl(pagos, reembolsos, liquidaciones, detalles, solicitudes, ejecutor, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @AfterEach
    void cerrarTransaccion() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void conReembolsoGuardado() {
        when(reembolsos.saveAndFlush(any())).thenAnswer(invocacion -> {
            Reembolso reembolso = invocacion.getArgument(0);
            ReflectionTestUtils.setField(reembolso, "id", REEMBOLSO_ID);
            return reembolso;
        });
        when(solicitudes.crear(REEMBOLSO_ID, Disparo.AUTOMATICO, USUARIO_ID, IP)).thenReturn(comando);
    }

    private static Pago pagado() {
        var pago = Pago.de(RESERVA_ID, "sim-123", new BigDecimal("161.00"), EstadoPago.PAGADO, "1234",
                Instant.parse("2026-10-28T15:00:00Z"));
        ReflectionTestUtils.setField(pago, "id", PAGO_ID);
        return pago;
    }

    private static ReembolsoPorCancelacion solicitud(String monto) {
        return new ReembolsoPorCancelacion(RESERVA_ID, new BigDecimal(monto), USUARIO_ID, IP);
    }

    @Test
    @DisplayName("HU-15 R24: audita REEMBOLSO_SOLICITADO sobre el pago PAGADO con origen, reserva y monto")
    void auditaLaSolicitud() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        conReembolsoGuardado();

        servicio.reembolsarCancelacion(solicitud("161.00"));

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "REEMBOLSO_SOLICITADO", "pago", PAGO_ID, IP,
                "origen=CANCELACION;reservaId=7;monto=161.00"));
    }

    @Test
    @DisplayName("HU-15 R24: no cambia el estado del pago ni lo guarda")
    void noTocaElPago() {
        var pago = pagado();
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pago));
        conReembolsoGuardado();

        servicio.reembolsarCancelacion(solicitud("161.00"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
        verify(pagos, never()).save(any());
    }

    @Test
    @DisplayName("HU-15 R24: no depende de PasarelaPago (HU-16 hará la llamada)")
    void sinPasarela() {
        var constructores = PagoServiceImpl.class.getDeclaredConstructors();

        assertThat(constructores).allSatisfy(c -> assertThat(c.getParameterTypes()).doesNotContain(PasarelaPago.class));
        assertThat(Arrays.stream(PagoServiceImpl.class.getDeclaredFields()).map(Field::getType).toList())
                .allSatisfy(tipo -> assertThat(tipo).isNotEqualTo(PasarelaPago.class));
    }

    @Test
    @DisplayName("HU-15 R24: se une a la transacción del llamador (MANDATORY)")
    void exigeTransaccion() throws Exception {
        var metodo = PagoServiceImpl.class.getMethod("reembolsarCancelacion", ReembolsoPorCancelacion.class);

        assertThat(metodo.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    @DisplayName("HU-15 R25 · HU-16 R6: sin pago PAGADO lanza IllegalStateException, no audita ni crea reembolso")
    void sinPagoPagado() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("161.00")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(auditoria, reembolsos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-15 R24 · HU-16 R6: un monto de 0 lanza IllegalArgumentException, no audita ni crea reembolso")
    void montoCero() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria, reembolsos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-15 R24 · HU-16 R6: un monto mayor que el pago lanza IllegalArgumentException sin reembolso")
    void montoMayorQueElPago() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("161.01")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria, reembolsos, solicitudes, ejecutor);
    }

    // ── HU-16 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-16 R6: un monto negativo lanza IllegalArgumentException sin crear reembolso ni comando")
    void montoNegativo() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria, reembolsos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-16 R1: guarda el reembolso PENDIENTE de CANCELACION, sin intentos, con el monto y la hora del reloj")
    void guardaElReembolsoPendiente() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        conReembolsoGuardado();

        servicio.reembolsarCancelacion(solicitud("161.00"));

        var guardado = ArgumentCaptor.forClass(Reembolso.class);
        verify(reembolsos).saveAndFlush(guardado.capture());
        var reembolso = guardado.getValue();
        assertThat(reembolso.getPagoId()).isEqualTo(PAGO_ID);
        assertThat(reembolso.getMonto()).isEqualByComparingTo("161.00");
        assertThat(reembolso.getOrigen()).isEqualTo(OrigenReembolso.CANCELACION);
        assertThat(reembolso.getEstado()).isEqualTo(EstadoReembolso.PENDIENTE);
        assertThat(reembolso.getIntentos()).isZero();
        assertThat(reembolso.getCreadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-16 R20: si el pago ya tiene un reembolso por cancelación lanza IllegalStateException sin guardar "
            + "ni crear el comando")
    void yaTieneReembolso() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        when(reembolsos.existsByPagoIdAndOrigen(PAGO_ID, OrigenReembolso.CANCELACION)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("161.00")))
                .isInstanceOf(IllegalStateException.class);

        verify(reembolsos, never()).saveAndFlush(any());
        verify(reembolsos, never()).save(any());
        verifyNoInteractions(solicitudes, ejecutor, auditoria);
    }

    @Test
    @DisplayName("HU-16 R3, R40: entrega al Invoker un comando AUTOMATICO con quien canceló y su IP, sin ejecutarlo")
    void entregaElComandoParaDespuesDelCommit() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        conReembolsoGuardado();

        servicio.reembolsarCancelacion(solicitud("161.00"));

        verify(solicitudes).crear(REEMBOLSO_ID, Disparo.AUTOMATICO, USUARIO_ID, IP);
        verify(ejecutor).ejecutarDespuesDelCommit(comando);
        verify(ejecutor, never()).ejecutarAhora(any());
        verifyNoInteractions(comando);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    private static Pago pagoConId(long id, long reservaId, EstadoPago estado, String monto, Instant creadoEn) {
        var pago = Pago.de(reservaId, "mp-" + id, new BigDecimal(monto), estado, "4321", creadoEn);
        ReflectionTestUtils.setField(pago, "id", id);
        return pago;
    }

    @Test
    @DisplayName("HU-22 R6: pagosDeReservas devuelve cada pago con monto, estado, fecha y sus reembolsos")
    void pagosDeReservasConReembolsos() {
        var reembolsado = pagoConId(31L, 7L, EstadoPago.REEMBOLSADO, "100.00", AHORA);
        var rechazado = pagoConId(32L, 8L, EstadoPago.RECHAZADO, "50.00", AHORA.plusSeconds(60));
        when(pagos.findByReservaIdInOrderByCreadoEnAscIdAsc(List.of(7L, 8L)))
                .thenReturn(List.of(reembolsado, rechazado));
        when(reembolsos.findByPagoIdInOrderByCreadoEnAscIdAsc(List.of(31L, 32L))).thenReturn(List.of(
                Reembolso.porCancelacion(31L, new BigDecimal("100.00"), AHORA.plusSeconds(120))));

        var resultado = servicio.pagosDeReservas(List.of(7L, 8L));

        assertThat(resultado).containsExactly(
                new PagoDeReserva(7L, new BigDecimal("100.00"), "REEMBOLSADO", AHORA, List.of(
                        new ReembolsoDePago(new BigDecimal("100.00"), "PENDIENTE", AHORA.plusSeconds(120)))),
                new PagoDeReserva(8L, new BigDecimal("50.00"), "RECHAZADO", AHORA.plusSeconds(60), List.of()));
    }

    @Test
    @DisplayName("HU-22 R6: sin reservas, pagosDeReservas devuelve una lista vacía sin consultar")
    void pagosDeReservasVacio() {
        assertThat(servicio.pagosDeReservas(List.of())).isEmpty();
        verifyNoInteractions(pagos, reembolsos);
    }

    @Test
    @DisplayName("HU-22 R13: pagosDeReservas no expone ultimos4 ni el id de la pasarela")
    void pagosDeReservasSinDatosDeTarjeta() {
        assertThat(PagoDeReserva.class.getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("reservaId", "monto", "estado", "creadoEn", "reembolsos");
        assertThat(ReembolsoDePago.class.getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("monto", "estado", "creadoEn");
    }

    @Test
    @DisplayName("HU-22 R6: pagosDeReservas es de solo lectura")
    void pagosDeReservasSoloLectura() throws Exception {
        var anotacion = PagoServiceImpl.class.getMethod("pagosDeReservas", Collection.class)
                .getAnnotation(Transactional.class);
        assertThat(anotacion.readOnly()).isTrue();
    }

    // ── HU-26 ────────────────────────────────────────────────────────────

    private static ReembolsoPorReclamo porReclamo(String monto) {
        return new ReembolsoPorReclamo(RESERVA_ID, new BigDecimal(monto), USUARIO_ID, IP);
    }

    @Test
    @DisplayName("HU-26 R44: reembolsarReclamo guarda un reembolso RECLAMO PENDIENTE por el monto pedido")
    void reclamoGuardaElReembolso() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        conReembolsoGuardado();

        servicio.reembolsarReclamo(porReclamo("80.25"));

        var guardado = ArgumentCaptor.forClass(Reembolso.class);
        verify(reembolsos).saveAndFlush(guardado.capture());
        var reembolso = guardado.getValue();
        assertThat(reembolso.getPagoId()).isEqualTo(PAGO_ID);
        assertThat(reembolso.getMonto()).isEqualByComparingTo("80.25");
        assertThat(reembolso.getOrigen()).isEqualTo(OrigenReembolso.RECLAMO);
        assertThat(reembolso.getEstado()).isEqualTo(EstadoReembolso.PENDIENTE);
        assertThat(reembolso.getIntentos()).isZero();
        assertThat(reembolso.getCreadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-26 R44: reembolsarReclamo audita REEMBOLSO_SOLICITADO con origen RECLAMO, reserva y monto")
    void reclamoAudita() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        conReembolsoGuardado();

        servicio.reembolsarReclamo(porReclamo("161.00"));

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "REEMBOLSO_SOLICITADO", "pago", PAGO_ID, IP,
                "origen=RECLAMO;reservaId=7;monto=161.00"));
    }

    @Test
    @DisplayName("HU-26 R45: reembolsarReclamo entrega al Invoker un comando AUTOMATICO para después del commit")
    void reclamoEntregaElComando() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        conReembolsoGuardado();

        servicio.reembolsarReclamo(porReclamo("161.00"));

        verify(solicitudes).crear(REEMBOLSO_ID, Disparo.AUTOMATICO, USUARIO_ID, IP);
        verify(ejecutor).ejecutarDespuesDelCommit(comando);
        verify(ejecutor, never()).ejecutarAhora(any());
        verifyNoInteractions(comando);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @DisplayName("HU-26 R44: un monto de 0 o mayor que el pago lanza IllegalArgumentException sin reembolso")
    void reclamoMontoFueraDeRango() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        assertThatThrownBy(() -> servicio.reembolsarReclamo(porReclamo("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.reembolsarReclamo(porReclamo("161.01")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria, reembolsos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-26 R44: sin pago PAGADO lanza IllegalStateException sin reembolso")
    void reclamoSinPago() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reembolsarReclamo(porReclamo("10.00")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(auditoria, reembolsos, solicitudes, ejecutor);
    }

    @Test
    @DisplayName("HU-26 R44: un segundo reembolso RECLAMO del mismo pago lanza IllegalStateException sin guardar")
    void reclamoYaReembolsado() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));
        when(reembolsos.existsByPagoIdAndOrigen(PAGO_ID, OrigenReembolso.RECLAMO)).thenReturn(true);

        assertThatThrownBy(() -> servicio.reembolsarReclamo(porReclamo("10.00")))
                .isInstanceOf(IllegalStateException.class);
        verify(reembolsos, never()).saveAndFlush(any());
        verifyNoInteractions(solicitudes, ejecutor, auditoria);
    }

    @Test
    @DisplayName("HU-26 R8: reservaLiquidada pregunta a liquidacion_detalle por la reserva")
    void reservaLiquidada() {
        when(detalles.existsByReservaId(RESERVA_ID)).thenReturn(true);

        assertThat(servicio.reservaLiquidada(RESERVA_ID)).isTrue();
        verify(detalles).existsByReservaId(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-26 R21: bloquearGeneracionLiquidaciones toma el mismo bloqueo consultivo que la generación")
    void bloqueaLaGeneracion() {
        servicio.bloquearGeneracionLiquidaciones();

        verify(liquidaciones).bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION);
        assertThat(LiquidacionRepository.CLAVE_GENERACION).isEqualTo(190_019L);
    }

    @Test
    @DisplayName("HU-26 R21, R44: los tres métodos nuevos se unen a la transacción del llamador (MANDATORY)")
    void metodosNuevosExigenTransaccion() throws Exception {
        assertThat(PagoServiceImpl.class.getMethod("reembolsarReclamo", ReembolsoPorReclamo.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(PagoServiceImpl.class.getMethod("bloquearGeneracionLiquidaciones")
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(PagoServiceImpl.class.getMethod("reservaLiquidada", Long.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
